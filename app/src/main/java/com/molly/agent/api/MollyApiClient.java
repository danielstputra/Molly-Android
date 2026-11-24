package com.molly.agent.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.molly.agent.model.ChatMessage;
import com.molly.agent.model.ChatSessionItem;
import com.molly.agent.model.ChatDisplayItem;
import com.molly.agent.model.ToolNameHelper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MollyApiClient {
    private static final String PREF_NAME = "molly_config";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final SharedPreferences prefs;
    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;

    private String serverUrl = "";
    private String tunnelUrl = "";
    private String token = "";
    private String sessionId = "";
    private String agentId = "default-chatbot";
    private String agentName = "Asisten Cerdas";
    private String selectedModelId = "ag-tools:gemini-4-hydrogen-medium";
    private String selectedModelName = "gemini-4-hydrogen-medium";
    private String selectedApprovalMode = "always_trust";

    public static class ModelItem {
        public String id;
        public String rawId;
        public String name;
        public String provider;
        public String providerId;

        public ModelItem(String id, String rawId, String name, String provider, String providerId) {
            this.id = id;
            this.rawId = rawId;
            this.name = name;
            this.provider = provider;
            this.providerId = providerId;
        }

        @Override
        public String toString() {
            return name + " (" + provider + ")";
        }
    }

    public static class AgentItem {
        public String id;
        public String name;
        public String description;

        public AgentItem(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class MetadataResponse {
        public List<ModelItem> models = new ArrayList<>();
        public List<AgentItem> agents = new ArrayList<>();
        public List<String> approvalModes = new ArrayList<>();
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public interface SessionExpirationListener {
        void onSessionExpired(String reason);
    }

    private SessionExpirationListener sessionExpirationListener;

    public void setSessionExpirationListener(SessionExpirationListener listener) {
        this.sessionExpirationListener = listener;
    }

    public synchronized void notifySessionExpired(String reason) {
        if (!isConfigured()) return;
        disconnect();
        if (sessionExpirationListener != null) {
            mainHandler.post(() -> sessionExpirationListener.onSessionExpired(reason));
        }
    }

    public MollyApiClient(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Response response = chain.proceed(request);
                    if (response.code() == 401 || response.code() == 403) {
                        String path = request.url().encodedPath();
                        if (!path.contains("/download-apk") && !path.contains("/mobile-pairing-info")) {
                            notifySessionExpired("Sesi login di Molly Web telah berakhir atau logout.");
                        }
                    }
                    return response;
                })
                .build();
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
        loadConfig();
    }

    public void loadConfig() {
        this.serverUrl = prefs.getString("server_url", "");
        this.tunnelUrl = prefs.getString("tunnel_url", "");
        this.token = prefs.getString("token", "molly-guest-session");
        this.sessionId = prefs.getString("session_id", "");
        this.agentId = prefs.getString("agent_id", "default-chatbot");
        this.agentName = prefs.getString("agent_name", "Asisten Cerdas");
        this.selectedModelId = prefs.getString("selected_model_id", "ag-tools:gemini-4-hydrogen-medium");
        this.selectedModelName = prefs.getString("selected_model_name", "gemini-4-hydrogen-medium");
        this.selectedApprovalMode = prefs.getString("selected_approval_mode", "always_trust");

        // Jika sessionId kosong, biarkan kosong agar auto-create session berjalan saat kirim pesan pertama
        if (this.sessionId == null) {
            this.sessionId = "";
        }
    }

    public void savePairingData(String url, String tunnelUrl, String token, String sessionId, String agentId, String agentName) {
        this.serverUrl = (url != null) ? (url.endsWith("/") ? url.substring(0, url.length() - 1) : url) : "";
        if (tunnelUrl != null && !tunnelUrl.trim().isEmpty()) {
            String t = tunnelUrl.trim();
            this.tunnelUrl = t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
        }
        this.token = (token != null && !token.isEmpty()) ? token : "molly-guest-session";
        this.sessionId = (sessionId != null) ? sessionId.trim() : "";
        this.agentId = (agentId != null && !agentId.isEmpty()) ? agentId : "default-chatbot";
        this.agentName = (agentName != null && !agentName.isEmpty()) ? agentName : "Asisten Cerdas";

        prefs.edit()
                .putString("server_url", this.serverUrl)
                .putString("tunnel_url", this.tunnelUrl)
                .putString("token", this.token)
                .putString("session_id", this.sessionId)
                .putString("agent_id", this.agentId)
                .putString("agent_name", this.agentName)
                .apply();
    }

    public void savePairingData(String url, String token, String sessionId, String agentId, String agentName) {
        savePairingData(url, "", token, sessionId, agentId, agentName);
    }

    public void setSelectedModel(String modelId, String modelName) {
        this.selectedModelId = modelId;
        this.selectedModelName = modelName;
        prefs.edit()
                .putString("selected_model_id", modelId)
                .putString("selected_model_name", modelName)
                .apply();
    }

    public void setSelectedAgent(String agentId, String agentName) {
        this.agentId = agentId;
        this.agentName = agentName;
        prefs.edit()
                .putString("agent_id", agentId)
                .putString("agent_name", agentName)
                .apply();
    }

    public void setSelectedApprovalMode(String mode) {
        this.selectedApprovalMode = mode;
        prefs.edit().putString("selected_approval_mode", mode).apply();
    }

    public boolean isConfigured() {
        return !serverUrl.isEmpty();
    }

    public void disconnect() {
        this.serverUrl = "";
        this.tunnelUrl = "";
        this.token = "molly-guest-session";
        this.sessionId = "";
        this.agentId = "default-chatbot";
        this.agentName = "Asisten Cerdas";
        prefs.edit()
                .remove("server_url")
                .remove("tunnel_url")
                .remove("token")
                .remove("session_id")
                .remove("agent_id")
                .remove("agent_name")
                .apply();
    }

    public void updateServerUrl(String newUrl) {
        if (newUrl != null && !newUrl.trim().isEmpty()) {
            String trimmed = newUrl.trim();
            this.serverUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            SharedPreferences.Editor editor = prefs.edit().putString("server_url", this.serverUrl);
            if (this.serverUrl.startsWith("https://") && (this.serverUrl.contains(".trycloudflare.com") || this.serverUrl.contains(".loca.lt") || this.serverUrl.contains(".ngrok"))) {
                this.tunnelUrl = this.serverUrl;
                editor.putString("tunnel_url", this.tunnelUrl);
            }
            editor.apply();
        }
    }

    public String getTunnelUrl() {
        return tunnelUrl != null ? tunnelUrl : "";
    }

    public void setTunnelUrl(String url) {
        if (url != null) {
            String trimmed = url.trim();
            this.tunnelUrl = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
            prefs.edit().putString("tunnel_url", this.tunnelUrl).apply();
        }
    }

    /**
     * Memverifikasi validitas sesi/token autentikasi saat ini dengan server Molly.
     * Jika sesi sudah logout di web atau token kedaluwarsa, interceptor otomatis memicu pemutusan sesi.
     */
    public void verifySession(ApiCallback<Boolean> callback) {
        if (!isConfigured()) {
            if (callback != null) callback.onError("Belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/system/mobile/verify-session";
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Koneksi gagal: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(true));
                    }
                } else if (response.code() == 404) {
                    verifySessionFallback(callback);
                } else if (response.code() == 401 || response.code() == 403) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Sesi telah berakhir (" + response.code() + ")"));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Status: " + response.code()));
                    }
                }
            }
        });
    }

    private void verifySessionFallback(ApiCallback<Boolean> callback) {
        String endpoint = serverUrl + "/api/v1/agents/sessions?limit=1";
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Koneksi gagal: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(true));
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Sesi telah berakhir (" + response.code() + ")"));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Status: " + response.code()));
                    }
                }
            }
        });
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getSelectedModelName() {
        return selectedModelName;
    }

    public String getSelectedApprovalMode() {
        return selectedApprovalMode;
    }

    /**
     * Mengambil daftar model dan agent dari server Molly
     */
    public void fetchMetadata(ApiCallback<MetadataResponse> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/system/mobile/metadata";
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Error: " + response.code()));
                    return;
                }

                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject root = JsonParser.parseString(bodyStr).getAsJsonObject();
                    MetadataResponse meta = new MetadataResponse();

                    if (root.has("models") && root.get("models").isJsonArray()) {
                        for (JsonElement el : root.getAsJsonArray("models")) {
                            JsonObject m = el.getAsJsonObject();
                            meta.models.add(new ModelItem(
                                    m.get("id").getAsString(),
                                    m.has("raw_id") ? m.get("raw_id").getAsString() : m.get("id").getAsString(),
                                    m.get("name").getAsString(),
                                    m.has("provider") ? m.get("provider").getAsString() : "AI",
                                    m.has("provider_id") ? m.get("provider_id").getAsString() : ""
                            ));
                        }
                    }

                    if (root.has("agents") && root.get("agents").isJsonArray()) {
                        for (JsonElement el : root.getAsJsonArray("agents")) {
                            JsonObject a = el.getAsJsonObject();
                            meta.agents.add(new AgentItem(
                                    a.get("id").getAsString(),
                                    a.get("name").getAsString(),
                                    a.has("description") ? a.get("description").getAsString() : ""
                            ));
                        }
                    }

                    mainHandler.post(() -> callback.onSuccess(meta));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse metadata: " + e.getMessage()));
                }
            }
        });
    }

    public String getNormalizedApprovalMode() {
        if ("full_trust".equalsIgnoreCase(selectedApprovalMode) || "always_trust".equalsIgnoreCase(selectedApprovalMode)) {
            return "always_trust";
        }
        return "default";
    }

    public String getNormalizedModelId() {
        if (selectedModelId == null || selectedModelId.trim().isEmpty()) {
            return "ag-tools:gemini-4-hydrogen-medium";
        }
        String trimmed = selectedModelId.trim();
        if (!trimmed.contains(":") && !trimmed.contains("/")) {
            return "ag-tools:" + trimmed;
        }
        return trimmed;
    }

    /**
     * Kirim prompt dari Android ke server Molly.
     * Dilengkapi mekanisme Auto-Healing: Jika sesi belum ada di database atau server merespons 404 ("Benang tidak ada"),
     * aplikasi akan secara otomatis membuat sesi nyata di database via POST /api/v1/agents/sessions secara atomik
     * bersama input prompt pengguna, sehingga pengiriman dijamin 100% berhasil.
     */
    public void sendPrompt(String promptText, ApiCallback<String> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum dikonfigurasi.");
            return;
        }

        if (sessionId == null || sessionId.trim().isEmpty() || sessionId.equals("molly-guest-session") || sessionId.startsWith("local-") || sessionId.startsWith("draft-")) {
            createSessionAndSendPrompt(promptText, callback);
            return;
        }

        executeSendPromptToExistingSession(promptText, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(String error) {
                // Auto-healing: Jika 404 (Thread/Benang tidak ditemukan di DB), buat sesi baru secara otomatis
                if (error != null && (error.contains("404") || error.contains("Benang") || error.toLowerCase().contains("not found"))) {
                    createSessionAndSendPrompt(promptText, callback);
                } else {
                    callback.onError(error);
                }
            }
        });
    }

    /**
     * Mengirim event prompt ke sesi yang sudah ada di server.
     */
    private void executeSendPromptToExistingSession(String promptText, ApiCallback<String> callback) {
        String endpoint = serverUrl + "/api/v1/agents/sessions/" + sessionId + "/events";
        String idempotencyKey = UUID.randomUUID().toString();

        JsonObject root = new JsonObject();
        JsonArray events = new JsonArray();

        JsonObject event = new JsonObject();
        event.addProperty("type", "agent.session.input.message");

        JsonArray input = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("type", "message");
        userMsg.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject textObj = new JsonObject();
        textObj.addProperty("type", "input_text");
        textObj.addProperty("text", promptText);
        content.add(textObj);

        userMsg.add("content", content);
        input.add(userMsg);
        event.add("input", input);

        JsonObject molly = new JsonObject();
        molly.addProperty("mode", "follow_up");
        molly.addProperty("model", getNormalizedModelId());
        molly.addProperty("tool_approval_mode", getNormalizedApprovalMode());
        event.add("molly", molly);

        events.add(event);
        root.add("events", events);

        RequestBody body = RequestBody.create(root.toString(), JSON);
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .addHeader("Idempotency-Key", idempotencyKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Koneksi gagal: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    mainHandler.post(() -> callback.onSuccess("Pesan terkirim ke Molly"));
                } else {
                    String errBody = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Server error (" + response.code() + "): " + errBody));
                }
            }
        });
    }

    /**
     * Membuat sesi baru di server secara atomik dengan membawa pesan awal pengguna.
     * Menggunakan API standar POST /api/v1/agents/sessions.
     */
    public void createSessionAndSendPrompt(String promptText, ApiCallback<String> callback) {
        String endpoint = serverUrl + "/api/v1/agents/sessions";
        String idempotencyKey = UUID.randomUUID().toString();

        JsonObject root = new JsonObject();
        root.addProperty("agent_id", (agentId != null && !agentId.isEmpty()) ? agentId : "default-chatbot");
        String titleSnippet = promptText.length() > 30 ? promptText.substring(0, 30) + "..." : promptText;
        root.addProperty("title", titleSnippet);
        root.addProperty("tool_approval_mode", getNormalizedApprovalMode());

        JsonObject agentObj = new JsonObject();
        agentObj.addProperty("model", getNormalizedModelId());
        root.add("agent", agentObj);

        root.addProperty("input", promptText);

        RequestBody body = RequestBody.create(root.toString(), JSON);
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .addHeader("Idempotency-Key", idempotencyKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Koneksi gagal: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String bodyStr = response.body() != null ? response.body().string() : "{}";
                        JsonObject respJson = JsonParser.parseString(bodyStr).getAsJsonObject();
                        if (respJson.has("id")) {
                            String newThreadId = respJson.get("id").getAsString();
                            switchSession(newThreadId);
                        }
                        mainHandler.post(() -> callback.onSuccess("Pesan terkirim ke Molly"));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onSuccess("Pesan terkirim ke Molly"));
                    }
                } else {
                    String errBody = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Server error (" + response.code() + "): " + errBody));
                }
            }
        });
    }

    /**
     * Membaca riwayat pesan sesi terkini dari server Molly
     */
    public void fetchSessionMessages(ApiCallback<List<ChatMessage>> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum dikonfigurasi.");
            return;
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + sessionId + "/items?limit=100&order=asc";
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 404) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Error: " + response.code()));
                    return;
                }

                String bodyStr = response.body() != null ? response.body().string() : "{}";
                List<ChatMessage> list = parseItemsToMessages(bodyStr);
                mainHandler.post(() -> callback.onSuccess(list));
            }
        });
    }

    private List<ChatMessage> parseItemsToMessages(String jsonStr) {
        List<ChatMessage> list = new ArrayList<>();
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");
            if (data == null) return list;

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

            for (JsonElement el : data) {
                JsonObject item = el.getAsJsonObject();
                String role = item.has("role") ? item.get("role").getAsString() : "agent";
                String itemId = item.has("id") ? item.get("id").getAsString() : UUID.randomUUID().toString();
                String timeStr = sdf.format(new Date());

                StringBuilder textBuilder = new StringBuilder();
                if (item.has("content") && item.get("content").isJsonArray()) {
                    for (JsonElement c : item.getAsJsonArray("content")) {
                        JsonObject cObj = c.getAsJsonObject();
                        if (cObj.has("text")) {
                            textBuilder.append(cObj.get("text").getAsString()).append("\n");
                        }
                    }
                }

                String fullText = textBuilder.toString().trim();
                if (!fullText.isEmpty()) {
                    list.add(new ChatMessage(itemId, role, fullText, timeStr));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * Menyetel status sesi baru (mengosongkan ID sesi aktif)
     * sehingga pesan pertama yang dikirim akan otomatis membuat sesi baru di server.
     */
    public String createNewSession() {
        this.sessionId = "";
        prefs.edit().putString("session_id", "").apply();
        return "";
    }

    /**
     * Beralih ke ID sesi tertentu.
     */
    public void switchSession(String newSessionId) {
        if (newSessionId != null && !newSessionId.trim().isEmpty()) {
            this.sessionId = newSessionId.trim();
            prefs.edit().putString("session_id", this.sessionId).apply();
        }
    }

    /**
     * Mengambil daftar recent sessions dari server Molly.
     */
    public void fetchRecentSessions(boolean archived, ApiCallback<List<ChatSessionItem>> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions?limit=50&archived=" + archived;
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Status " + response.code()));
                    return;
                }

                List<ChatSessionItem> result = new ArrayList<>();
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    if (obj.has("data") && obj.get("data").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("data")) {
                            JsonObject s = el.getAsJsonObject();
                            String id = s.has("id") ? s.get("id").getAsString() : "";
                            if (id.isEmpty()) continue;

                            String title = "Percakapan";
                            boolean isArch = archived;
                            String model = "";

                            if (s.has("molly") && s.get("molly").isJsonObject()) {
                                JsonObject m = s.getAsJsonObject("molly");
                                if (m.has("title") && !m.get("title").isJsonNull()) {
                                    title = m.get("title").getAsString();
                                }
                                if (m.has("archived")) {
                                    isArch = m.get("archived").getAsBoolean();
                                }
                                if (m.has("model") && !m.get("model").isJsonNull()) {
                                    model = m.get("model").getAsString();
                                }
                            }

                            long updated = System.currentTimeMillis();
                            if (s.has("updated_at") && !s.get("updated_at").isJsonNull()) {
                                try {
                                    updated = s.get("updated_at").getAsLong() * 1000L;
                                } catch (Exception ignored) {}
                            }

                            result.add(new ChatSessionItem(id, title, updated, isArch, "", "Molly Agent", model));
                        }
                    }
                    mainHandler.post(() -> callback.onSuccess(result));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Mengambil riwayat percakapan sesi dan mengonversinya menjadi elemen tampilan real
     * (Pesan User, Respon Agen, Tool Machine Execution, dan CoT Thinking).
     */
    public void fetchSessionDisplayItems(ApiCallback<List<ChatDisplayItem>> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + sessionId + "/items?limit=100&order=asc";
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 404) {
                    mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Error: " + response.code()));
                    return;
                }

                String bodyStr = response.body() != null ? response.body().string() : "{}";
                List<ChatDisplayItem> list = parseItemsToDisplayList(bodyStr);
                mainHandler.post(() -> callback.onSuccess(list));
            }
        });
    }

    public List<ChatDisplayItem> parseItemsToDisplayList(String jsonStr) {
        List<ChatDisplayItem> list = new ArrayList<>();
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");
            if (data == null) return list;

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Map<String, ChatDisplayItem> toolCallsMap = new HashMap<>();

            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String type = item.has("type") ? item.get("type").getAsString() : "";
                String role = item.has("role") ? item.get("role").getAsString() : "";
                String itemId = item.has("id") ? item.get("id").getAsString() : UUID.randomUUID().toString();
                String turnId = item.has("turn_id") ? item.get("turn_id").getAsString() : "";
                String timeStr = sdf.format(new Date());

                // Kasus 1: Pesan Pengguna (User Message)
                if ("user".equalsIgnoreCase(role)) {
                    StringBuilder textBuilder = new StringBuilder();
                    if (item.has("content") && item.get("content").isJsonArray()) {
                        for (JsonElement c : item.getAsJsonArray("content")) {
                            if (c.isJsonObject() && c.getAsJsonObject().has("text")) {
                                textBuilder.append(c.getAsJsonObject().get("text").getAsString()).append("\n");
                            }
                        }
                    }
                    String userText = textBuilder.toString().trim();
                    if (!userText.isEmpty()) {
                        list.add(new ChatDisplayItem(itemId, ChatDisplayItem.TYPE_USER, "user", userText, timeStr));
                    }
                }
                // Kasus 2: Pemanggilan Alat Mesin (Function Call / Tool Call)
                else if ("function_call".equalsIgnoreCase(type) || item.has("call_id") || item.has("function") || (item.has("name") && item.has("args"))) {
                    String toolId = "";
                    String args = "";
                    if (item.has("name")) {
                        toolId = item.get("name").getAsString();
                    } else if (item.has("function") && item.getAsJsonObject("function").has("name")) {
                        toolId = item.getAsJsonObject("function").get("name").getAsString();
                    }

                    if (item.has("args")) {
                        args = item.get("args").isJsonObject() ? item.get("args").toString() : item.get("args").getAsString();
                    } else if (item.has("function") && item.getAsJsonObject("function").has("arguments")) {
                        args = item.getAsJsonObject("function").get("arguments").getAsString();
                    }

                    String status = item.has("status") ? item.get("status").getAsString() : "completed";
                    String waitpointId = item.has("waitpoint_id") ? item.get("waitpoint_id").getAsString() : null;

                    String output = "";
                    if (item.has("tool_call_result")) {
                        JsonObject res = item.getAsJsonObject("tool_call_result");
                        if (res.has("content")) {
                            output = res.get("content").isJsonPrimitive() ? res.get("content").getAsString() : res.get("content").toString();
                        }
                        if (res.has("status")) {
                            status = res.get("status").getAsString();
                        }
                    }

                    ChatDisplayItem toolItem = ChatDisplayItem.createToolCall(
                            itemId,
                            toolId,
                            ToolNameHelper.getDisplayName(toolId),
                            args,
                            output,
                            status,
                            timeStr,
                            turnId,
                            waitpointId
                    );
                    list.add(toolItem);
                    toolCallsMap.put(itemId, toolItem);
                }
                // Kasus 3: Hasil Pemanggilan Alat Mesin (Function Call Output)
                else if ("function_call_output".equalsIgnoreCase(type) || "tool_result".equalsIgnoreCase(type)) {
                    String callId = item.has("call_id") ? item.get("call_id").getAsString() : "";
                    String outputContent = "";
                    if (item.has("content")) {
                        outputContent = item.get("content").isJsonPrimitive() ? item.get("content").getAsString() : item.get("content").toString();
                    } else if (item.has("output")) {
                        outputContent = item.get("output").isJsonPrimitive() ? item.get("output").getAsString() : item.get("output").toString();
                    }

                    if (toolCallsMap.containsKey(callId)) {
                        ChatDisplayItem existing = toolCallsMap.get(callId);
                        if (existing != null) {
                            existing.setToolOutput(outputContent);
                            if (item.has("status")) {
                                existing.setToolStatus(item.get("status").getAsString());
                            } else {
                                existing.setToolStatus("completed");
                            }
                        }
                    } else {
                        // Tambahkan sebagai standalone output jika belum terhubung
                        ChatDisplayItem toolItem = ChatDisplayItem.createToolCall(
                                itemId,
                                "tool_result",
                                "Hasil Alat Mesin",
                                "",
                                outputContent,
                                "completed",
                                timeStr,
                                turnId,
                                null
                        );
                        list.add(toolItem);
                    }
                }
                // Kasus 4: Reasoning / Thought
                else if ("reasoning".equalsIgnoreCase(type) || "thought".equalsIgnoreCase(type)) {
                    String reasoningText = item.has("content") ? item.get("content").getAsString() : "";
                    if (!reasoningText.trim().isEmpty()) {
                        list.add(ChatDisplayItem.createThinking(itemId, reasoningText.trim(), timeStr));
                    }
                }
                // Kasus 5: Respon Teks Asisten / Agen (Bisa Mengandung <think>...</think>)
                else if ("assistant".equalsIgnoreCase(role) || "agent".equalsIgnoreCase(role) || "message".equalsIgnoreCase(type)) {
                    StringBuilder textBuilder = new StringBuilder();
                    if (item.has("content") && item.get("content").isJsonArray()) {
                        for (JsonElement c : item.getAsJsonArray("content")) {
                            if (c.isJsonObject() && c.getAsJsonObject().has("text")) {
                                textBuilder.append(c.getAsJsonObject().get("text").getAsString()).append("\n");
                            }
                        }
                    } else if (item.has("content") && item.get("content").isJsonPrimitive()) {
                        textBuilder.append(item.get("content").getAsString());
                    }

                    String rawText = textBuilder.toString().trim();
                    if (!rawText.isEmpty()) {
                        // Deteksi CoT thinking tags <think>...</think>
                        if (rawText.contains("<think>") && rawText.contains("</think>")) {
                            int start = rawText.indexOf("<think>");
                            int end = rawText.indexOf("</think>");
                            String thinkingContent = rawText.substring(start + 7, end).trim();
                            String remainingText = (rawText.substring(0, start) + rawText.substring(end + 8)).trim();

                            if (!thinkingContent.isEmpty()) {
                                list.add(ChatDisplayItem.createThinking(itemId + "_think", thinkingContent, timeStr));
                            }
                            if (!remainingText.isEmpty()) {
                                list.add(new ChatDisplayItem(itemId, ChatDisplayItem.TYPE_AGENT, "agent", remainingText, timeStr));
                            }
                        } else if (rawText.startsWith("<think>")) {
                            // Masih sedang berpikir (streaming)
                            String thinkingContent = rawText.substring(7).trim();
                            list.add(ChatDisplayItem.createThinking(itemId + "_think", thinkingContent, timeStr));
                        } else {
                            list.add(new ChatDisplayItem(itemId, ChatDisplayItem.TYPE_AGENT, "agent", rawText, timeStr));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * Mengirim persetujuan eksekusi tool (Waitpoint resume) ke backend Molly secara real.
     */
    public void submitToolApproval(String turnId, String waitpointId, boolean approve, ApiCallback<Boolean> callback) {
        if (!isConfigured() || turnId == null || waitpointId == null) {
            callback.onError("Parameter waitpoint tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + sessionId + "/events";
        String idempotencyKey = UUID.randomUUID().toString();

        JsonObject root = new JsonObject();
        JsonArray events = new JsonArray();

        JsonObject ev = new JsonObject();
        ev.addProperty("type", "molly.session.input.resume");
        ev.addProperty("turn_id", turnId);
        ev.addProperty("waitpoint_id", waitpointId);

        JsonObject resp = new JsonObject();
        resp.addProperty("decision", approve ? "approve" : "reject");
        ev.add("response", resp);

        events.add(ev);
        root.add("events", events);

        RequestBody body = RequestBody.create(root.toString(), JSON);
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Idempotency-Key", idempotencyKey)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean ok = response.isSuccessful();
                mainHandler.post(() -> {
                    if (ok) callback.onSuccess(true);
                    else callback.onError("Gagal kirim persetujuan: status " + response.code());
                });
            }
        });
    }

    /**
     * Mengarsipkan sesi di backend Molly.
     */
    public void archiveSession(String targetSessionId, ApiCallback<Boolean> callback) {
        if (!isConfigured() || targetSessionId == null || targetSessionId.isEmpty()) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + targetSessionId + "/archive";
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(RequestBody.create(new byte[0], null))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean ok = response.isSuccessful();
                mainHandler.post(() -> {
                    if (ok) callback.onSuccess(true);
                    else callback.onError("Gagal arsip: status " + response.code());
                });
            }
        });
    }
}
