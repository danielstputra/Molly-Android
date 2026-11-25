package com.molly.agent.model;

import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScheduledRunItem {
    public String id;
    public String jobId;
    public String status; // "completed", "running", "dispatching", "failed", "skipped", "pending"
    public String trigger; // "manual", "schedule"
    public String scheduledFor;
    public String threadId;
    public boolean sessionAvailable;
    public String errorMessage;

    public static ScheduledRunItem fromJson(JsonObject obj) {
        ScheduledRunItem item = new ScheduledRunItem();
        if (obj.has("id")) item.id = obj.get("id").getAsString();
        if (obj.has("job_id")) item.jobId = obj.get("job_id").getAsString();
        if (obj.has("status")) item.status = obj.get("status").getAsString();
        if (obj.has("trigger")) item.trigger = obj.get("trigger").getAsString();
        if (obj.has("scheduled_for") && !obj.get("scheduled_for").isJsonNull()) {
            item.scheduledFor = obj.get("scheduled_for").getAsString();
        }
        if (obj.has("thread_id") && !obj.get("thread_id").isJsonNull()) {
            item.threadId = obj.get("thread_id").getAsString();
        }
        if (obj.has("session_available")) {
            item.sessionAvailable = obj.get("session_available").getAsBoolean();
        }
        if (obj.has("error_message") && !obj.get("error_message").isJsonNull()) {
            item.errorMessage = obj.get("error_message").getAsString();
        }
        return item;
    }

    public String getStatusLabel() {
        if (status == null) return "Menunggu";
        switch (status.toLowerCase()) {
            case "completed": return "Selesai";
            case "running": return "Berjalan";
            case "dispatching":
            case "submitted":
            case "queued": return "Mengantre";
            case "skipped": return "Dilewati";
            case "failed": return "Gagal";
            case "cancelled": return "Dibatalkan";
            default: return status;
        }
    }

    public int getStatusColor() {
        if (status == null) return 0xFF9CA3AF;
        switch (status.toLowerCase()) {
            case "completed": return 0xFF10B981; // Green
            case "running":
            case "dispatching":
            case "submitted":
            case "queued": return 0xFFF59E0B; // Yellow/Amber
            case "skipped": return 0xFF9CA3AF; // Gray
            case "failed":
            case "cancelled": return 0xFFEF4444; // Red
            default: return 0xFF38BDF8;
        }
    }

    public String getFormattedTime() {
        if (scheduledFor == null || scheduledFor.isEmpty()) return "Menunggu jadwal";
        try {
            // ISO 8601 parsing fallback
            String clean = scheduledFor.replace("Z", "+00:00");
            if (clean.length() >= 16) {
                return clean.substring(0, 10) + " " + clean.substring(11, 16);
            }
            return clean;
        } catch (Exception e) {
            return scheduledFor;
        }
    }
}
