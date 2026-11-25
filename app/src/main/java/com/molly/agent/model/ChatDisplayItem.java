package com.molly.agent.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChatDisplayItem {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AGENT = 2;
    public static final int TYPE_TOOL_CALL = 3;
    public static final int TYPE_THINKING = 4;
    public static final int TYPE_TOOL_GROUP = 5;

    public static class SubToolCall {
        public String id;
        public String toolId;
        public String toolName;
        public String args;
        public String output;
        public String status; // "completed", "in_progress", "waiting", "error"
        public String turnId;
        public String waitpointId;
        public boolean isExpanded;

        public SubToolCall(String id, String toolId, String toolName, String args, String output, String status, String turnId, String waitpointId) {
            this.id = id;
            this.toolId = toolId;
            this.toolName = (toolName != null && !toolName.isEmpty()) ? toolName : toolId;
            this.args = args != null ? args : "";
            this.output = output != null ? output : "";
            this.status = status != null ? status : "completed";
            this.turnId = turnId;
            this.waitpointId = waitpointId;
            this.isExpanded = false;
        }

        public String getSummaryArgs() {
            if (args == null || args.trim().isEmpty()) return "";
            try {
                com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(args.trim());
                if (el.isJsonObject()) {
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    List<String> parts = new ArrayList<>();
                    if (obj.has("query")) parts.add(obj.get("query").getAsString());
                    if (obj.has("pattern")) parts.add(obj.get("pattern").getAsString());
                    if (obj.has("command")) parts.add(obj.get("command").getAsString());
                    if (obj.has("path")) parts.add(obj.get("path").getAsString());
                    if (obj.has("file_path")) parts.add(obj.get("file_path").getAsString());
                    if (obj.has("text")) parts.add(obj.get("text").getAsString());
                    if (!parts.isEmpty()) {
                        return " | " + String.join(" | ", parts);
                    }
                }
            } catch (Exception ignored) {}
            String clean = args.replace("\n", " ").trim();
            return clean.length() > 50 ? " | " + clean.substring(0, 50) + "..." : " | " + clean;
        }

        public String getSummaryOutput() {
            if (output == null || output.trim().isEmpty()) {
                if ("completed".equalsIgnoreCase(status)) return "No matches found";
                if ("in_progress".equalsIgnoreCase(status)) return "Mengeksekusi...";
                if ("waiting".equalsIgnoreCase(status)) return "Menunggu persetujuan";
                return "";
            }
            String trimmed = output.trim();
            if (trimmed.toLowerCase().contains("no matches") || trimmed.toLowerCase().contains("tidak ditemukan") || trimmed.equals("[]") || trimmed.equals("{}")) {
                return "No matches found";
            }
            if (trimmed.length() > 120) {
                return trimmed.substring(0, 120) + "...";
            }
            return trimmed;
        }

        public String getToolIcon() {
            if (toolId == null) return "🔧";
            String lower = toolId.toLowerCase();
            if (lower.contains("message") || lower.contains("chat") || lower.contains("history")) return "💬";
            if (lower.contains("file") || lower.contains("grep") || lower.contains("read") || lower.contains("dir")) return "📁";
            if (lower.contains("bash") || lower.contains("cmd") || lower.contains("shell") || lower.contains("run")) return "⚡";
            if (lower.contains("search") || lower.contains("web")) return "🔍";
            if (lower.contains("image")) return "🎨";
            return "⚛";
        }
    }

    private String id;
    private int itemType;
    private String sender;
    private String text;
    private String timestamp;

    // Field untuk Tool Call / Tool Machine
    private String toolId;
    private String toolName;
    private String toolArgs;
    private String toolOutput;
    private String toolStatus; // "completed", "in_progress", "waiting", "error"
    private String turnId;
    private String waitpointId;
    private boolean isExpanded;
    private int feedback = 0; // 0 = none, 1 = thumbs up, -1 = thumbs down

    // Sub-tools untuk Grouping Tool Call (Gambar 3)
    private final List<SubToolCall> subTools = new ArrayList<>();

    public ChatDisplayItem(String id, int itemType, String sender, String text, String timestamp) {
        this.id = id;
        this.itemType = itemType;
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.isExpanded = false;
    }

    public static ChatDisplayItem createToolGroup(String id, String timestamp, String turnId) {
        ChatDisplayItem item = new ChatDisplayItem(id, TYPE_TOOL_GROUP, "tool_group", "", timestamp);
        item.turnId = turnId;
        item.isExpanded = false; // default tertutup (collapse) seperti Gambar 3
        return item;
    }

    public void addSubTool(SubToolCall sub) {
        this.subTools.add(sub);
        if ("waiting".equalsIgnoreCase(sub.status) || "in_progress".equalsIgnoreCase(sub.status)) {
            this.isExpanded = true;
        }
    }

    public List<SubToolCall> getSubTools() {
        return subTools;
    }

    public String getGroupSummaryTitle() {
        int count = subTools.size();
        Set<String> uniqueToolNames = new LinkedHashSet<>();
        for (SubToolCall s : subTools) {
            if (s.toolName != null && !s.toolName.trim().isEmpty()) {
                uniqueToolNames.add(s.toolName.trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        if (count == 1 && !uniqueToolNames.isEmpty()) {
            sb.append(uniqueToolNames.iterator().next());
        } else {
            sb.append(count).append(" tools called");
            for (String name : uniqueToolNames) {
                sb.append(" · ").append(name);
            }
        }
        return sb.toString();
    }

    public boolean hasWaitingApproval() {
        for (SubToolCall s : subTools) {
            if ("waiting".equalsIgnoreCase(s.status)) return true;
        }
        return false;
    }

    public static ChatDisplayItem createToolCall(String id, String toolId, String toolName, String args, String output, String status, String timestamp, String turnId, String waitpointId) {
        ChatDisplayItem item = new ChatDisplayItem(id, TYPE_TOOL_CALL, "tool", "", timestamp);
        item.toolId = toolId;
        item.toolName = (toolName != null && !toolName.isEmpty()) ? toolName : toolId;
        item.toolArgs = args != null ? args : "";
        item.toolOutput = output != null ? output : "";
        item.toolStatus = status != null ? status : "completed";
        item.turnId = turnId;
        item.waitpointId = waitpointId;
        item.isExpanded = true;
        return item;
    }

    public static ChatDisplayItem createThinking(String id, String thinkingText, String timestamp) {
        ChatDisplayItem item = new ChatDisplayItem(id, TYPE_THINKING, "agent", thinkingText, timestamp);
        item.isExpanded = false;
        return item;
    }

    public String getId() {
        return id;
    }

    public int getItemType() {
        return itemType;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getToolId() {
        return toolId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolArgs() {
        return toolArgs;
    }

    public void setToolArgs(String toolArgs) {
        this.toolArgs = toolArgs;
    }

    public String getToolOutput() {
        return toolOutput;
    }

    public void setToolOutput(String toolOutput) {
        this.toolOutput = toolOutput;
    }

    public String getToolStatus() {
        return toolStatus;
    }

    public void setToolStatus(String toolStatus) {
        this.toolStatus = toolStatus;
    }

    public String getTurnId() {
        return turnId;
    }

    public String getWaitpointId() {
        return waitpointId;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public int getFeedback() {
        return feedback;
    }

    public void setFeedback(int feedback) {
        this.feedback = feedback;
    }
}
