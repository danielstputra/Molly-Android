package com.molly.agent.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ProviderModelItem {
    public String id = "";
    public String displayName = "";
    public String type = "chat"; // chat, embedding, rerank
    public String source = "remote"; // remote, manual
    public boolean enabled = true;
    public Integer contextLength = null;
    public Integer dimension = null;
    public Integer batchSize = null;

    // Transient UI status
    public String testStatus = null; // available, unavailable, unsupported, error, testing
    public String testMessage = null;

    public ProviderModelItem() {}

    public ProviderModelItem(String id, String displayName, String type) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.enabled = true;
    }

    public static ProviderModelItem fromJson(JsonObject obj) {
        ProviderModelItem item = new ProviderModelItem();
        if (obj.has("id")) item.id = obj.get("id").getAsString();
        if (obj.has("display_name") && !obj.get("display_name").isJsonNull()) {
            item.displayName = obj.get("display_name").getAsString();
        } else {
            item.displayName = item.id;
        }
        if (obj.has("type") && !obj.get("type").isJsonNull()) {
            item.type = obj.get("type").getAsString();
        }
        if (obj.has("source") && !obj.get("source").isJsonNull()) {
            item.source = obj.get("source").getAsString();
        }
        if (obj.has("enabled")) {
            item.enabled = obj.get("enabled").getAsBoolean();
        } else {
            item.enabled = true;
        }
        if (obj.has("context_length") && !obj.get("context_length").isJsonNull()) {
            try { item.contextLength = obj.get("context_length").getAsInt(); } catch (Exception ignored) {}
        }
        if (obj.has("dimension") && !obj.get("dimension").isJsonNull()) {
            try { item.dimension = obj.get("dimension").getAsInt(); } catch (Exception ignored) {}
        }
        if (obj.has("batch_size") && !obj.get("batch_size").isJsonNull()) {
            try { item.batchSize = obj.get("batch_size").getAsInt(); } catch (Exception ignored) {}
        }
        return item;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("display_name", displayName);
        obj.addProperty("type", type);
        obj.addProperty("source", source);
        obj.addProperty("enabled", enabled);
        if (contextLength != null) obj.addProperty("context_length", contextLength);
        if (dimension != null) obj.addProperty("dimension", dimension);
        if (batchSize != null) obj.addProperty("batch_size", batchSize);
        return obj;
    }

    public String getTypeLabel() {
        if ("embedding".equalsIgnoreCase(type)) return "Embedding";
        if ("rerank".equalsIgnoreCase(type)) return "Rerank";
        return "Obrolan (Chat)";
    }

    public int getTypeBadgeColor() {
        if ("embedding".equalsIgnoreCase(type)) return 0xFF8B5CF6; // Purple
        if ("rerank".equalsIgnoreCase(type)) return 0xFFEC4899; // Pink
        return 0xFF38BDF8; // Sky Blue
    }

    public String getMetaDescription() {
        StringBuilder sb = new StringBuilder();
        if (contextLength != null && contextLength > 0) {
            if (contextLength >= 1048576) {
                sb.append((contextLength / 1048576)).append("M tokens");
            } else if (contextLength >= 1024) {
                sb.append((contextLength / 1024)).append("K tokens");
            } else {
                sb.append(contextLength).append(" tokens");
            }
        }
        if (dimension != null && dimension > 0) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append("Dimensi ").append(dimension);
        }
        return sb.toString();
    }
}
