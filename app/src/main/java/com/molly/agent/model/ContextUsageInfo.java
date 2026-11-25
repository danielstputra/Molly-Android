package com.molly.agent.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;

/**
 * Model data penggunaan konteks dan token yang diselaraskan 100%
 * dengan contextUsage.js dan token_usage.py di Web Molly.
 */
public class ContextUsageInfo {
    public static final long TOKEN_COUNT_K_UNIT = 1024;
    public static final long TOKEN_COUNT_M_UNIT = 1024 * 1000; // 1,024,000

    public int percent = 0;
    public long usedTokens = 0;
    public long limitTokens = 262144; // 256K default (262144 / 1024 = 256K)
    public long totalModelUsage = 0;
    public int cacheHitRatio = 0;

    public String modelName = "";
    public int modelCallCount = 0;
    public long modelInputTokens = 0;
    public long modelOutputTokens = 0;
    public long modelCacheTokens = 0;
    public int modelCacheHitRatio = -1;
    public long modelReasoningTokens = 0;

    public long systemTokens = 0;
    public long toolsTokens = 0;
    public int toolsCount = 0;
    public long contentTokens = 0;
    public int contentCount = 0;
    public long toolMsgTokens = 0;
    public int toolMsgCount = 0;
    public boolean summaryActive = false;
    public long summaryTokens = 0;

    public long recentInputTokens = 0;
    public long recentOutputTokens = 0;

    /**
     * Memformat jumlah token persis dengan formatTokenCount di contextUsage.js Web Molly:
     * - Menggunakan basis 1024 (TOKEN_COUNT_K_UNIT = 1024, TOKEN_COUNT_M_UNIT = 1024 * 1000)
     * - Presisi 3 angka signifikan (toPrecision(3))
     */
    public static String formatToken(long value) {
        if (value <= 0) return "0";
        if (value >= TOKEN_COUNT_M_UNIT) {
            double val = value / (double) TOKEN_COUNT_M_UNIT;
            return formatToPrecision3(val) + "M";
        }
        if (value >= TOKEN_COUNT_K_UNIT) {
            double val = value / (double) TOKEN_COUNT_K_UNIT;
            return formatToPrecision3(val) + "K";
        }
        return String.valueOf(value);
    }

    private static String formatToPrecision3(double val) {
        if (val >= 100.0) {
            return String.valueOf(Math.round(val));
        } else if (val >= 10.0) {
            String formatted = String.format(Locale.US, "%.1f", val);
            return formatted.replaceAll("\\.0$", "");
        } else {
            String formatted = String.format(Locale.US, "%.2f", val);
            if (formatted.endsWith("0")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
            if (formatted.endsWith(".0")) {
                formatted = formatted.substring(0, formatted.length() - 2);
            }
            return formatted;
        }
    }

    /**
     * Mengurai objek JSON dari endpoint /api/v1/agents/sessions/{sessionId}/state
     * struktur: root.agent_state.token_usage atau root.token_usage
     */
    public static ContextUsageInfo parse(JsonObject root) {
        ContextUsageInfo info = new ContextUsageInfo();
        if (root == null) return info;

        JsonObject usage = null;
        if (root.has("agent_state") && root.get("agent_state").isJsonObject()) {
            JsonObject as = root.getAsJsonObject("agent_state");
            if (as.has("token_usage") && as.get("token_usage").isJsonObject()) {
                usage = as.getAsJsonObject("token_usage");
            }
        } else if (root.has("token_usage") && root.get("token_usage").isJsonObject()) {
            usage = root.getAsJsonObject("token_usage");
        }

        if (usage == null) return info;

        // 1. Batas Konteks & Tekanan Konteks Terkini
        if (usage.has("summary_trigger_tokens") && !usage.get("summary_trigger_tokens").isJsonNull()) {
            info.limitTokens = Math.max(1024, usage.get("summary_trigger_tokens").getAsLong());
        } else if (usage.has("context_window") && !usage.get("context_window").isJsonNull()) {
            info.limitTokens = Math.max(1024, usage.get("context_window").getAsLong());
        }

        if (usage.has("next_llm_input_tokens") && !usage.get("next_llm_input_tokens").isJsonNull()) {
            info.usedTokens = Math.max(0, usage.get("next_llm_input_tokens").getAsLong());
        } else if (usage.has("llm_input_tokens") && !usage.get("llm_input_tokens").isJsonNull()) {
            info.usedTokens = Math.max(0, usage.get("llm_input_tokens").getAsLong());
        }

        if (usage.has("context_usage_ratio") && !usage.get("context_usage_ratio").isJsonNull()) {
            double ratio = usage.get("context_usage_ratio").getAsDouble();
            info.percent = (int) Math.round(Math.max(0, Math.min(ratio, 1.0)) * 100.0);
        } else if (info.limitTokens > 0) {
            info.percent = (int) Math.round(Math.max(0, Math.min((info.usedTokens * 100.0) / info.limitTokens, 100.0)));
        }

        // 2. Thread Total Usage & Cache Hit Ratio Kumulatif
        if (usage.has("thread") && usage.get("thread").isJsonObject()) {
            JsonObject thread = usage.getAsJsonObject("thread");
            if (thread.has("total") && thread.get("total").isJsonObject()) {
                JsonObject total = thread.getAsJsonObject("total");
                if (total.has("total_tokens")) {
                    info.totalModelUsage = total.get("total_tokens").getAsLong();
                } else {
                    long inp = total.has("input_tokens") ? total.get("input_tokens").getAsLong() : 0;
                    long out = total.has("output_tokens") ? total.get("output_tokens").getAsLong() : 0;
                    info.totalModelUsage = inp + out;
                }
            }

            // 3. Models Breakdown
            if (thread.has("models") && thread.get("models").isJsonObject()) {
                JsonObject models = thread.getAsJsonObject("models");
                long totalObservedInput = 0;
                long totalCacheRead = 0;
                long totalObservedCalls = 0;

                for (Map.Entry<String, JsonElement> entry : models.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject bucket = entry.getValue().getAsJsonObject();

                    String bucketKey = entry.getKey();
                    String mSpec = bucketKey;
                    if (bucket.has("model") && bucket.get("model").isJsonObject()) {
                        JsonObject mIdentity = bucket.getAsJsonObject("model");
                        if (mIdentity.has("configured_model_spec") && !mIdentity.get("configured_model_spec").isJsonNull()) {
                            mSpec = mIdentity.get("configured_model_spec").getAsString();
                        }
                    }

                    int callCount = 0;
                    if (bucket.has("model_call_count") && !bucket.get("model_call_count").isJsonNull()) {
                        callCount = bucket.get("model_call_count").getAsInt();
                    }

                    long inputTokens = 0;
                    long outputTokens = 0;
                    long reasoning = 0;
                    if (bucket.has("usage") && bucket.get("usage").isJsonObject()) {
                        JsonObject uObj = bucket.getAsJsonObject("usage");
                        if (uObj.has("input_tokens") && !uObj.get("input_tokens").isJsonNull()) {
                            inputTokens = uObj.get("input_tokens").getAsLong();
                        }
                        if (uObj.has("output_tokens") && !uObj.get("output_tokens").isJsonNull()) {
                            outputTokens = uObj.get("output_tokens").getAsLong();
                        }
                        if (uObj.has("output_token_details") && uObj.get("output_token_details").isJsonObject()) {
                            JsonObject otd = uObj.getAsJsonObject("output_token_details");
                            if (otd.has("reasoning") && !otd.get("reasoning").isJsonNull()) {
                                reasoning = otd.get("reasoning").getAsLong();
                            }
                        }
                    }

                    long cacheRead = (bucket.has("cache_read_input_tokens") && !bucket.get("cache_read_input_tokens").isJsonNull())
                            ? bucket.get("cache_read_input_tokens").getAsLong() : 0;
                    long cacheObserved = (bucket.has("cache_observed_input_tokens") && !bucket.get("cache_observed_input_tokens").isJsonNull())
                            ? bucket.get("cache_observed_input_tokens").getAsLong() : 0;
                    long obsCalls = (bucket.has("cache_observed_call_count") && !bucket.get("cache_observed_call_count").isJsonNull())
                            ? bucket.get("cache_observed_call_count").getAsLong() : 0;

                    int bucketCacheRatio = -1;
                    if (bucket.has("cache_hit_ratio") && !bucket.get("cache_hit_ratio").isJsonNull()) {
                        bucketCacheRatio = (int) Math.round(bucket.get("cache_hit_ratio").getAsDouble() * 100);
                    }

                    // Gunakan model pertama yang memiliki panggilan atau data aktif
                    if (info.modelName.isEmpty() || (info.modelCallCount == 0 && callCount > 0)) {
                        info.modelName = mSpec;
                        info.modelCallCount = callCount;
                        info.modelInputTokens = inputTokens;
                        info.modelOutputTokens = outputTokens;
                        info.modelCacheTokens = cacheRead;
                        info.modelReasoningTokens = reasoning;
                        info.modelCacheHitRatio = bucketCacheRatio;
                    }

                    totalCacheRead += cacheRead;
                    totalObservedInput += cacheObserved;
                    totalObservedCalls += obsCalls;
                }

                if (totalObservedCalls > 0 && totalObservedInput > 0) {
                    info.cacheHitRatio = (int) Math.round((totalCacheRead * 100.0) / totalObservedInput);
                } else if (info.modelCacheHitRatio >= 0) {
                    info.cacheHitRatio = info.modelCacheHitRatio;
                }
            }
        }

        // 4. Komposisi Konteks Terkini (Recent Context Composition)
        if (usage.has("system_tokens") && !usage.get("system_tokens").isJsonNull()) {
            info.systemTokens = usage.get("system_tokens").getAsLong();
        } else if (usage.has("system_instruction_tokens") && !usage.get("system_instruction_tokens").isJsonNull()) {
            info.systemTokens = usage.get("system_instruction_tokens").getAsLong();
        }

        if (usage.has("tools_tokens") && !usage.get("tools_tokens").isJsonNull()) {
            info.toolsTokens = usage.get("tools_tokens").getAsLong();
        }

        if (usage.has("tool_count") && !usage.get("tool_count").isJsonNull()) {
            info.toolsCount = usage.get("tool_count").getAsInt();
        } else if (usage.has("tools_count") && !usage.get("tools_count").isJsonNull()) {
            info.toolsCount = usage.get("tools_count").getAsInt();
        }

        if (usage.has("llm_content_message_tokens") && !usage.get("llm_content_message_tokens").isJsonNull()) {
            info.contentTokens = usage.get("llm_content_message_tokens").getAsLong();
        }
        if (usage.has("llm_content_message_count") && !usage.get("llm_content_message_count").isJsonNull()) {
            info.contentCount = usage.get("llm_content_message_count").getAsInt();
        }

        if (usage.has("llm_tool_message_tokens") && !usage.get("llm_tool_message_tokens").isJsonNull()) {
            info.toolMsgTokens = usage.get("llm_tool_message_tokens").getAsLong();
        }
        if (usage.has("llm_tool_message_count") && !usage.get("llm_tool_message_count").isJsonNull()) {
            info.toolMsgCount = usage.get("llm_tool_message_count").getAsInt();
        }

        if (usage.has("summary_active") && !usage.get("summary_active").isJsonNull() && usage.get("summary_active").getAsBoolean()) {
            info.summaryActive = true;
            if (usage.has("summary_message_tokens") && !usage.get("summary_message_tokens").isJsonNull()) {
                info.summaryTokens = usage.get("summary_message_tokens").getAsLong();
            }
        }

        // 5. Panggilan Terakhir (Recent Call / latest)
        if (usage.has("latest") && usage.get("latest").isJsonObject()) {
            JsonObject latest = usage.getAsJsonObject("latest");
            if (latest.has("usage") && latest.get("usage").isJsonObject()) {
                JsonObject u = latest.getAsJsonObject("usage");
                if (u.has("input_tokens") && !u.get("input_tokens").isJsonNull()) {
                    info.recentInputTokens = u.get("input_tokens").getAsLong();
                }
                if (u.has("output_tokens") && !u.get("output_tokens").isJsonNull()) {
                    info.recentOutputTokens = u.get("output_tokens").getAsLong();
                }
            }
        }

        return info;
    }
}
