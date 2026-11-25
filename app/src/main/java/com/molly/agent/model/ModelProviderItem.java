package com.molly.agent.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ModelProviderItem {
    public int id;
    public String providerId;
    public String displayName;
    public String providerType; // "openai", "anthropic"
    public String defaultProtocol;
    public String baseUrl;
    public String embeddingBaseUrl;
    public String rerankBaseUrl;
    public String modelsEndpoint;
    public String embeddingModelsEndpoint;
    public String rerankModelsEndpoint;
    public String apiKeyEnv;
    public boolean apiKeyConfigured;
    public String apiKey;
    public List<String> capabilities = new ArrayList<>();
    public List<ProviderModelItem> enabledModels = new ArrayList<>();
    public boolean isEnabled;
    public boolean isBuiltin;
    public String credentialStatus; // "ok", "warning", "error"

    public ModelProviderItem() {
        this.providerType = "openai";
        this.isEnabled = true;
        this.capabilities.add("chat");
        this.credentialStatus = "ok";
    }

    public static ModelProviderItem fromJson(JsonObject obj) {
        ModelProviderItem item = new ModelProviderItem();
        if (obj.has("id") && !obj.get("id").isJsonNull()) item.id = obj.get("id").getAsInt();
        if (obj.has("provider_id")) item.providerId = obj.get("provider_id").getAsString();
        if (obj.has("display_name")) item.displayName = obj.get("display_name").getAsString();
        if (obj.has("provider_type") && !obj.get("provider_type").isJsonNull()) item.providerType = obj.get("provider_type").getAsString();
        if (obj.has("default_protocol") && !obj.get("default_protocol").isJsonNull()) item.defaultProtocol = obj.get("default_protocol").getAsString();
        if (obj.has("base_url") && !obj.get("base_url").isJsonNull()) item.baseUrl = obj.get("base_url").getAsString();
        if (obj.has("embedding_base_url") && !obj.get("embedding_base_url").isJsonNull()) item.embeddingBaseUrl = obj.get("embedding_base_url").getAsString();
        if (obj.has("rerank_base_url") && !obj.get("rerank_base_url").isJsonNull()) item.rerankBaseUrl = obj.get("rerank_base_url").getAsString();
        if (obj.has("models_endpoint") && !obj.get("models_endpoint").isJsonNull()) item.modelsEndpoint = obj.get("models_endpoint").getAsString();
        if (obj.has("embedding_models_endpoint") && !obj.get("embedding_models_endpoint").isJsonNull()) item.embeddingModelsEndpoint = obj.get("embedding_models_endpoint").getAsString();
        if (obj.has("rerank_models_endpoint") && !obj.get("rerank_models_endpoint").isJsonNull()) item.rerankModelsEndpoint = obj.get("rerank_models_endpoint").getAsString();
        if (obj.has("api_key_env") && !obj.get("api_key_env").isJsonNull()) item.apiKeyEnv = obj.get("api_key_env").getAsString();
        if (obj.has("api_key_configured")) item.apiKeyConfigured = obj.get("api_key_configured").getAsBoolean();
        if (obj.has("api_key") && !obj.get("api_key").isJsonNull()) item.apiKey = obj.get("api_key").getAsString();
        if (obj.has("is_enabled")) item.isEnabled = obj.get("is_enabled").getAsBoolean();
        if (obj.has("is_builtin")) item.isBuiltin = obj.get("is_builtin").getAsBoolean();
        if (obj.has("credential_status") && !obj.get("credential_status").isJsonNull()) item.credentialStatus = obj.get("credential_status").getAsString();

        if (obj.has("capabilities") && obj.get("capabilities").isJsonArray()) {
            item.capabilities.clear();
            for (JsonElement el : obj.getAsJsonArray("capabilities")) {
                if (el.isJsonPrimitive()) item.capabilities.add(el.getAsString());
            }
        }

        if (obj.has("enabled_models") && obj.get("enabled_models").isJsonArray()) {
            item.enabledModels.clear();
            for (JsonElement el : obj.getAsJsonArray("enabled_models")) {
                if (el.isJsonObject()) {
                    item.enabledModels.add(ProviderModelItem.fromJson(el.getAsJsonObject()));
                }
            }
        }

        return item;
    }

    public String getAvatarEmoji() {
        if (providerId == null) return "🤖";
        String p = providerId.toLowerCase();
        if (p.contains("openai") || p.contains("gpt")) return "🟢";
        if (p.contains("anthropic") || p.contains("claude")) return "🟠";
        if (p.contains("gemini") || p.contains("google")) return "🔵";
        if (p.contains("deepseek")) return "🐋";
        if (p.contains("ollama")) return "🦙";
        if (p.contains("groq")) return "⚡";
        if (p.contains("silicon")) return "🔮";
        if (p.contains("mistral")) return "🌪️";
        if (p.contains("ag-tools") || p.contains("antigravity")) return "⚛️";
        return "🤖";
    }

    public String getCapabilitiesSummary() {
        if (capabilities == null || capabilities.isEmpty()) return "chat";
        return String.join(" • ", capabilities);
    }

    public String getCredentialBadgeText() {
        if ("warning".equalsIgnoreCase(credentialStatus)) {
            return "Perlu Kunci API";
        }
        if ("error".equalsIgnoreCase(credentialStatus)) {
            return "Kredensial Error";
        }
        return "Siap Digunakan";
    }

    public int getCredentialBadgeColor() {
        if ("warning".equalsIgnoreCase(credentialStatus)) {
            return 0xFFF59E0B; // Amber
        }
        if ("error".equalsIgnoreCase(credentialStatus)) {
            return 0xFFEF4444; // Red
        }
        return 0xFF10B981; // Green
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("provider_id", providerId);
        obj.addProperty("display_name", displayName);
        obj.addProperty("provider_type", providerType);
        if (defaultProtocol != null) obj.addProperty("default_protocol", defaultProtocol);
        obj.addProperty("base_url", baseUrl);
        if (embeddingBaseUrl != null) obj.addProperty("embedding_base_url", embeddingBaseUrl);
        if (rerankBaseUrl != null) obj.addProperty("rerank_base_url", rerankBaseUrl);
        obj.addProperty("models_endpoint", modelsEndpoint);
        if (apiKeyEnv != null && !apiKeyEnv.isEmpty()) obj.addProperty("api_key_env", apiKeyEnv);
        if (apiKey != null && !apiKey.isEmpty()) obj.addProperty("api_key", apiKey);
        obj.addProperty("is_enabled", isEnabled);

        JsonArray capArr = new JsonArray();
        for (String c : capabilities) capArr.add(c);
        obj.add("capabilities", capArr);

        JsonArray modelsArr = new JsonArray();
        for (ProviderModelItem m : enabledModels) {
            modelsArr.add(m.toJson());
        }
        obj.add("enabled_models", modelsArr);

        return obj;
    }

    public boolean hasCredentialWarning() {
        return "warning".equalsIgnoreCase(credentialStatus) || !apiKeyConfigured;
    }

    public int getActiveModelsCount() {
        return getEnabledModelCount();
    }

    public int getEnabledModelCount() {
        if (enabledModels == null) return 0;
        int count = 0;
        for (ProviderModelItem m : enabledModels) {
            if (m.enabled) count++;
        }
        return count > 0 ? count : enabledModels.size();
    }
}
