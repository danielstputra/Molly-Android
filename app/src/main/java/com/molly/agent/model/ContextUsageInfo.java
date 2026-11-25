package com.molly.agent.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

public class ContextUsageInfo {
    public int percent = 0;
    public long usedTokens = 0;
    public long limitTokens = 262144; // 256K default
    public long totalModelUsage = 0;
    public int cacheHitRatio = 0;

    public String modelName = "";
    public int modelCallCount = 0;
    public long modelInputTokens = 0;
    public long modelOutputTokens = 0;
    public long modelCacheTokens = 0;
    public long modelReasoningTokens = 0;

    public long systemTokens = 0;
    public long toolsTokens = 0;
    public int toolsCount = 0;
    public long contentTokens = 0;
    public int contentCount = 0;
    public long toolMsgTokens = 0;
    public int toolMsgCount = 0;

    public long recentInputTokens = 0;
    public long recentOutputTokens = 0;

    public static String formatToken(long value) {
        if (value <= 0) return "0";
        if (value >= 1_000_000) {
            double m = value / 1_000_000.0;
            return String.format(java.util.Locale.US, "%.2fM", m).replaceAll("\\.0+M", "M");
        }
        if (value >= 1_000) {
            double k = value / 1_000.0;
            return String.format(java.util.Locale.US, "%.1fK", k).replaceAll("\\.0+K", "K");
        }
        return String.valueOf(value);
    }

    public static ContextUsageInfo parse(JsonObject root) {
        ContextUsageInfo info = new ContextUsageInfo();
        if (root == null) return info;

        JsonObject usage = null;
        if (root.has("agent_state") && root.getAsJsonObject("agent_state").has("token_usage")) {
            usage = root.getAsJsonObject("agent_state").getAsJsonObject("token_usage");
        } else if (root.has("token_usage")) {
            usage = root.getAsJsonObject("token_usage");
        }

        if (usage == null) return info;

        // Limit & Pressure
        if (usage.has("summary_trigger_tokens")) {
            info.limitTokens = Math.max(1024, usage.get("summary_trigger_tokens").getAsLong());
        }
        if (usage.has("next_llm_input_tokens")) {
            info.usedTokens = usage.get("next_llm_input_tokens").getAsLong();
        } else if (usage.has("llm_input_tokens")) {
            info.usedTokens = usage.get("llm_input_tokens").getAsLong();
        }

        if (info.limitTokens > 0) {
            info.percent = (int) Math.min(100, Math.max(0, (info.usedTokens * 100.0) / info.limitTokens));
        }

        // Thread Totals
        if (usage.has("thread") && usage.getAsJsonObject("thread").has("total")) {
            JsonObject total = usage.getAsJsonObject("thread").getAsJsonObject("total");
            if (total.has("total_tokens")) {
                info.totalModelUsage = total.get("total_tokens").getAsLong();
            }
        }

        // Models
        if (usage.has("thread") && usage.getAsJsonObject("thread").has("models")) {
            JsonObject models = usage.getAsJsonObject("thread").getAsJsonObject("models");
            long totalCacheRead = 0;
            long totalCacheObserved = 0;

            for (Map.Entry<String, JsonElement> entry : models.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject mObj = entry.getValue().getAsJsonObject();

                if (info.modelName.isEmpty()) {
                    info.modelName = entry.getKey();
                    if (mObj.has("call_count")) info.modelCallCount = mObj.get("call_count").getAsInt();
                    if (mObj.has("input_tokens")) info.modelInputTokens = mObj.get("input_tokens").getAsLong();
                    if (mObj.has("output_tokens")) info.modelOutputTokens = mObj.get("output_tokens").getAsLong();
                    if (mObj.has("cache_read_input_tokens")) info.modelCacheTokens = mObj.get("cache_read_input_tokens").getAsLong();
                    if (mObj.has("reasoning_tokens")) info.modelReasoningTokens = mObj.get("reasoning_tokens").getAsLong();
                }

                if (mObj.has("cache_read_input_tokens")) {
                    totalCacheRead += mObj.get("cache_read_input_tokens").getAsLong();
                }
                if (mObj.has("cache_observed_input_tokens")) {
                    totalCacheObserved += mObj.get("cache_observed_input_tokens").getAsLong();
                }
            }

            if (totalCacheObserved > 0) {
                info.cacheHitRatio = (int) Math.min(100, Math.max(0, (totalCacheRead * 100.0) / totalCacheObserved));
            }
        }

        // Recent Context Composition Segments
        if (usage.has("system_instruction_tokens")) {
            info.systemTokens = usage.get("system_instruction_tokens").getAsLong();
        }
        if (usage.has("tools_tokens")) {
            info.toolsTokens = usage.get("tools_tokens").getAsLong();
        }
        if (usage.has("tools_count")) {
            info.toolsCount = usage.get("tools_count").getAsInt();
        }
        if (usage.has("llm_content_message_tokens")) {
            info.contentTokens = usage.get("llm_content_message_tokens").getAsLong();
        }
        if (usage.has("llm_content_message_count")) {
            info.contentCount = usage.get("llm_content_message_count").getAsInt();
        }
        if (usage.has("llm_tool_message_tokens")) {
            info.toolMsgTokens = usage.get("llm_tool_message_tokens").getAsLong();
        }
        if (usage.has("llm_tool_message_count")) {
            info.toolMsgCount = usage.get("llm_tool_message_count").getAsInt();
        }

        // Recent Call
        if (usage.has("recent_call") && usage.getAsJsonObject("recent_call").isJsonObject()) {
            JsonObject rc = usage.getAsJsonObject("recent_call");
            if (rc.has("input_tokens")) info.recentInputTokens = rc.get("input_tokens").getAsLong();
            if (rc.has("output_tokens")) info.recentOutputTokens = rc.get("output_tokens").getAsLong();
        }

        return info;
    }
}
