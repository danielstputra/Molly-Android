package com.molly.agent.model;

import com.google.gson.JsonObject;

public class ScheduledTaskItem {
    public String id;
    public String name;
    public String projectId;
    public String agentSlug;
    public String agentName;
    public String prompt;
    public String cronExpression;
    public String timezone;
    public boolean enabled;
    public String toolApprovalMode;
    public String modelSpec;
    public String createdAt;
    public String updatedAt;

    public ScheduledTaskItem() {
        this.enabled = true;
        this.timezone = "Asia/Jakarta";
        this.toolApprovalMode = "always_trust";
        this.cronExpression = "0 9 * * *";
    }

    public static ScheduledTaskItem fromJson(JsonObject obj) {
        ScheduledTaskItem item = new ScheduledTaskItem();
        if (obj.has("id")) item.id = obj.get("id").getAsString();
        if (obj.has("name")) item.name = obj.get("name").getAsString();
        if (obj.has("project_id")) item.projectId = obj.get("project_id").getAsString();
        if (obj.has("agent_slug")) item.agentSlug = obj.get("agent_slug").getAsString();
        if (obj.has("prompt")) item.prompt = obj.get("prompt").getAsString();
        if (obj.has("cron_expression")) item.cronExpression = obj.get("cron_expression").getAsString();
        if (obj.has("timezone")) item.timezone = obj.get("timezone").getAsString();
        if (obj.has("enabled")) item.enabled = obj.get("enabled").getAsBoolean();
        if (obj.has("tool_approval_mode")) item.toolApprovalMode = obj.get("tool_approval_mode").getAsString();
        if (obj.has("model_spec") && !obj.get("model_spec").isJsonNull()) {
            item.modelSpec = obj.get("model_spec").getAsString();
        }
        if (obj.has("created_at")) item.createdAt = obj.get("created_at").getAsString();
        if (obj.has("updated_at")) item.updatedAt = obj.get("updated_at").getAsString();
        return item;
    }

    public String getScheduleDescription() {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return "Jadwal belum ditentukan";
        }
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length != 5) return "Cron: " + cronExpression;

        String min = parts[0];
        String hour = parts[1];
        String dom = parts[2];
        String mon = parts[3];
        String dow = parts[4];

        String timeStr = String.format(java.util.Locale.US, "%02d:%02d",
                safeParseInt(hour, 9), safeParseInt(min, 0));

        if (dom.equals("*") && mon.equals("*") && dow.equals("*")) {
            return "Setiap hari pukul " + timeStr;
        }
        if (dom.equals("*") && mon.equals("*") && !dow.equals("*")) {
            return "Setiap " + formatWeekday(dow) + " pukul " + timeStr;
        }
        if (!dom.equals("*") && mon.equals("*") && dow.equals("*")) {
            return "Tanggal " + dom + " setiap bulan pukul " + timeStr;
        }
        return "Cron: " + cronExpression + " (" + timeStr + ")";
    }

    private static int safeParseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String formatWeekday(String dow) {
        switch (dow) {
            case "1": return "Senin";
            case "2": return "Selasa";
            case "3": return "Rabu";
            case "4": return "Kamis";
            case "5": return "Jumat";
            case "6": return "Sabtu";
            case "0":
            case "7": return "Minggu";
            default: return "hari (" + dow + ")";
        }
    }
}
