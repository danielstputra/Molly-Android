package com.molly.agent.model;

public class ChatDisplayItem {
    public static final int TYPE_USER = 1;
    public static final int TYPE_AGENT = 2;
    public static final int TYPE_TOOL_CALL = 3;
    public static final int TYPE_THINKING = 4;

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

    public ChatDisplayItem(String id, int itemType, String sender, String text, String timestamp) {
        this.id = id;
        this.itemType = itemType;
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.isExpanded = false;
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
}
