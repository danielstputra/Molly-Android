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
import com.molly.agent.model.ArtifactItem;
import com.molly.agent.model.AttachmentItem;
import com.molly.agent.model.ChatMessage;
import com.molly.agent.model.ChatSessionItem;
import com.molly.agent.model.ChatDisplayItem;
import com.molly.agent.model.ToolNameHelper;

import java.io.File;
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
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MollyApiClient {
    private static final String PREF_NAME = "molly_config";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static volatile MollyApiClient sInstance;

    public static MollyApiClient getInstance(Context context) {
        if (sInstance == null && context != null) {
            synchronized (MollyApiClient.class) {
                if (sInstance == null) {
                    sInstance = new MollyApiClient(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

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
                    if (response.code() == 401) {
                        String path = request.url().encodedPath();
                        // Hanya pemicu logout jika verifikasi sesi utama gagal 401
                        if (path.contains("/verify-session") || path.contains("/auth/status")) {
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

        if (this.serverUrl == null || this.serverUrl.trim().isEmpty()) {
            this.serverUrl = (this.tunnelUrl != null && !this.tunnelUrl.trim().isEmpty()) ? this.tunnelUrl : "";
        }
        if (this.serverUrl != null && !this.serverUrl.trim().isEmpty()) {
            this.serverUrl = this.serverUrl.trim();
            if (!this.serverUrl.startsWith("http://") && !this.serverUrl.startsWith("https://")) {
                this.serverUrl = "http://" + this.serverUrl;
            }
            if (this.serverUrl.endsWith("/")) {
                this.serverUrl = this.serverUrl.substring(0, this.serverUrl.length() - 1);
            }
        }

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

    /**
     * Mendaftarkan status pairing perangkat mobile ke backend Molly.
     */
    public void registerPairing(String targetSessionId, ApiCallback<Boolean> callback) {
        if (!isConfigured()) return;
        final String targetUrl = this.serverUrl;
        final String session = (targetSessionId != null && !targetSessionId.isEmpty()) ? targetSessionId : this.sessionId;
        final String authToken = this.token;

        String endpoint = targetUrl + "/api/system/mobile/pair";
        JsonObject body = new JsonObject();
        body.addProperty("session_id", session);
        body.addProperty("device_info", "Android " + android.os.Build.MODEL);

        RequestBody reqBody = RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + authToken)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(reqBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(response.isSuccessful()));
                }
            }
        });
    }

    /**
     * Mengirim notifikasi unpair/disconnect ke server Molly sebelum data lokal dihapus.
     */
    public void notifyDisconnect(ApiCallback<Boolean> callback) {
        if (!isConfigured()) {
            if (callback != null) callback.onSuccess(true);
            return;
        }
        final String targetUrl = this.serverUrl;
        final String targetSession = this.sessionId;
        final String targetToken = this.token;

        String endpoint = targetUrl + "/api/system/mobile/disconnect";
        JsonObject body = new JsonObject();
        body.addProperty("session_id", targetSession);

        RequestBody reqBody = RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + targetToken)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(reqBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(response.isSuccessful()));
                }
            }
        });
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
        return getBaseUrl();
    }

    public OkHttpClient getOkHttpClient() {
        return this.client;
    }

    public String getToken() {
        return (this.token != null) ? this.token : "";
    }

    public String getBaseUrl() {
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            String u = serverUrl.trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                u = "http://" + u;
            }
            if (u.endsWith("/")) {
                u = u.substring(0, u.length() - 1);
            }
            this.serverUrl = u;
            return u;
        }
        String url = prefs.getString("server_url", "");
        if (url == null || url.trim().isEmpty()) {
            url = (tunnelUrl != null && !tunnelUrl.trim().isEmpty()) ? tunnelUrl : prefs.getString("tunnel_url", "");
        }
        if (url == null) url = "";
        url = url.trim();
        if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.isEmpty()) {
            this.serverUrl = url;
        }
        return url;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentId() {
        return agentId != null ? agentId : "default-chatbot";
    }

    public String getSelectedModelName() {
        return selectedModelName;
    }

    public String getSelectedModelId() {
        return selectedModelId != null ? selectedModelId : "ag-tools:gemini-4-hydrogen-medium";
    }

    public String getSelectedApprovalMode() {
        return selectedApprovalMode;
    }

    public boolean isLocalIp(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase();
        return lower.contains("10.") || lower.contains("192.168.") || lower.contains("172.") ||
               lower.contains("127.0.0.1") || lower.contains("localhost");
    }

    public List<ModelItem> getCachedOrFallbackModels() {
        String cachedJson = prefs.getString("cached_metadata", "");
        if (!cachedJson.isEmpty()) {
            try {
                JsonObject root = JsonParser.parseString(cachedJson).getAsJsonObject();
                if (root.has("models") && root.get("models").isJsonArray()) {
                    List<ModelItem> list = new ArrayList<>();
                    for (JsonElement el : root.getAsJsonArray("models")) {
                        JsonObject m = el.getAsJsonObject();
                        list.add(new ModelItem(
                                m.get("id").getAsString(),
                                m.has("raw_id") ? m.get("raw_id").getAsString() : m.get("id").getAsString(),
                                m.get("name").getAsString(),
                                m.has("provider") ? m.get("provider").getAsString() : "AI",
                                m.has("provider_id") ? m.get("provider_id").getAsString() : ""
                        ));
                    }
                    if (!list.isEmpty()) return list;
                }
            } catch (Exception ignored) {}
        }

        List<ModelItem> fallback = new ArrayList<>();
        fallback.add(new ModelItem("ag-tools:gemini-4-hydrogen-medium", "gemini-4-hydrogen-medium", "gemini-4-hydrogen-medium", "Google", "ag-tools"));
        fallback.add(new ModelItem("ag-tools:gemini-2.5-flash", "gemini-2.5-flash", "gemini-2.5-flash", "Google", "ag-tools"));
        fallback.add(new ModelItem("ag-tools:gemini-2.5-pro", "gemini-2.5-pro", "gemini-2.5-pro", "Google", "ag-tools"));
        fallback.add(new ModelItem("ag-tools:claude-3-7-sonnet", "claude-3-7-sonnet", "claude-3-7-sonnet", "Anthropic", "ag-tools"));
        fallback.add(new ModelItem("ag-tools:claude-3-5-sonnet", "claude-3-5-sonnet", "claude-3-5-sonnet", "Anthropic", "ag-tools"));
        fallback.add(new ModelItem("ag-tools:gpt-4o", "gpt-4o", "gpt-4o", "OpenAI", "ag-tools"));
        fallback.add(new ModelItem("ag-tools:gpt-4o-mini", "gpt-4o-mini", "gpt-4o-mini", "OpenAI", "ag-tools"));
        fallback.add(new ModelItem("ag-tools:deepseek-r1", "deepseek-r1", "deepseek-r1", "DeepSeek", "ag-tools"));
        return fallback;
    }

    public List<AgentItem> getCachedOrFallbackAgents() {
        String cachedJson = prefs.getString("cached_metadata", "");
        if (!cachedJson.isEmpty()) {
            try {
                JsonObject root = JsonParser.parseString(cachedJson).getAsJsonObject();
                if (root.has("agents") && root.get("agents").isJsonArray()) {
                    List<AgentItem> list = new ArrayList<>();
                    for (JsonElement el : root.getAsJsonArray("agents")) {
                        JsonObject a = el.getAsJsonObject();
                        list.add(new AgentItem(
                                a.get("id").getAsString(),
                                a.get("name").getAsString(),
                                a.has("description") ? a.get("description").getAsString() : ""
                        ));
                    }
                    if (!list.isEmpty()) return list;
                }
            } catch (Exception ignored) {}
        }

        List<AgentItem> fallback = new ArrayList<>();
        fallback.add(new AgentItem("default-chatbot", "Asisten Cerdas", "Asisten AI Molly serbaguna"));
        fallback.add(new AgentItem("coder", "Code Architect", "Spesialis arsitektur sistem dan pemrograman"));
        fallback.add(new AgentItem("researcher", "Deep Researcher", "Riset komprehensif dan penalaran logis"));
        return fallback;
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
                // Auto-failover: Jika koneksi lokal timeout / unreachable dan ada tunnel_url, coba beralih otomatis
                if (isLocalIp(serverUrl) && !tunnelUrl.isEmpty() && !tunnelUrl.equalsIgnoreCase(serverUrl)) {
                    android.util.Log.w("MollyApiClient", "IP lokal timeout saat fetchMetadata. Auto-failover ke Tunnel: " + tunnelUrl);
                    updateServerUrl(tunnelUrl);
                    fetchMetadata(callback);
                    return;
                }

                // Jika gagal, kembalikan metadata dari cache lokal atau fallback agar UI tidak macet
                MetadataResponse fallbackMeta = new MetadataResponse();
                fallbackMeta.models = getCachedOrFallbackModels();
                fallbackMeta.agents = getCachedOrFallbackAgents();
                mainHandler.post(() -> callback.onSuccess(fallbackMeta));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    // Fallback to cache on error code
                    MetadataResponse fallbackMeta = new MetadataResponse();
                    fallbackMeta.models = getCachedOrFallbackModels();
                    fallbackMeta.agents = getCachedOrFallbackAgents();
                    mainHandler.post(() -> callback.onSuccess(fallbackMeta));
                    return;
                }

                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    prefs.edit().putString("cached_metadata", bodyStr).apply();
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
    /**
     * Mengunggah berkas lampiran (Image, Dokumen, Audio, Video, Berkas) ke server Molly.
     */
    public void uploadAttachment(byte[] fileBytes, String fileName, String mimeType, ApiCallback<AttachmentItem> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum dikonfigurasi.");
            return;
        }

        String endpoint = serverUrl + "/api/system/mobile/upload-attachment";
        String actualMime = (mimeType != null && !mimeType.isEmpty()) ? mimeType : "application/octet-stream";
        String actualName = (fileName != null && !fileName.isEmpty()) ? fileName : "attachment";

        RequestBody filePart = RequestBody.create(fileBytes, MediaType.parse(actualMime));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", actualName, filePart)
                .build();

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (isLocalIp(serverUrl) && !tunnelUrl.isEmpty() && !tunnelUrl.equalsIgnoreCase(serverUrl)) {
                    updateServerUrl(tunnelUrl);
                    uploadAttachment(fileBytes, fileName, mimeType, callback);
                    return;
                }
                mainHandler.post(() -> callback.onError("Gagal mengunggah berkas: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String bodyStr = response.body() != null ? response.body().string() : "{}";
                        JsonObject respJson = JsonParser.parseString(bodyStr).getAsJsonObject();
                        AttachmentItem item = new AttachmentItem();
                        if (respJson.has("file_id")) {
                            item.setId(respJson.get("file_id").getAsString());
                        } else if (respJson.has("id")) {
                            item.setId(respJson.get("id").getAsString());
                        }
                        item.setFileName(respJson.has("filename") ? respJson.get("filename").getAsString() : actualName);
                        item.setFileSize(respJson.has("bytes") ? respJson.get("bytes").getAsLong() : fileBytes.length);
                        item.setMimeType(respJson.has("mime_type") ? respJson.get("mime_type").getAsString() : actualMime);
                        item.setCategory(respJson.has("category") ? respJson.get("category").getAsString() : "file");
                        if (respJson.has("image_url") && !respJson.get("image_url").isJsonNull()) {
                            item.setImageUrl(respJson.get("image_url").getAsString());
                        }
                        if (respJson.has("thumbnail_url") && !respJson.get("thumbnail_url").isJsonNull()) {
                            item.setThumbnailUrl(respJson.get("thumbnail_url").getAsString());
                        }
                        item.setUploaded(true);
                        item.setUploading(false);
                        mainHandler.post(() -> callback.onSuccess(item));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Gagal memproses respon berkas: " + e.getMessage()));
                    }
                } else {
                    String errBody = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Server error (" + response.code() + "): " + errBody));
                }
            }
        });
    }

    public void sendPrompt(String promptText, ApiCallback<String> callback) {
        sendPrompt(promptText, null, callback);
    }

    public void sendPrompt(String promptText, List<AttachmentItem> attachments, ApiCallback<String> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum dikonfigurasi.");
            return;
        }

        if (sessionId == null || sessionId.trim().isEmpty() || sessionId.equals("molly-guest-session") || sessionId.startsWith("local-") || sessionId.startsWith("draft-")) {
            createSessionAndSendPrompt(promptText, attachments, callback);
            return;
        }

        executeSendPromptToExistingSession(promptText, attachments, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(String error) {
                // Auto-healing: Jika 404 (Thread/Benang tidak ditemukan di DB), buat sesi baru secara otomatis
                if (error != null && (error.contains("404") || error.contains("Benang") || error.toLowerCase().contains("not found"))) {
                    createSessionAndSendPrompt(promptText, attachments, callback);
                } else {
                    callback.onError(error);
                }
            }
        });
    }

    /**
     * Mengirim event prompt ke sesi yang sudah ada di server (dengan lampiran berkas).
     */
    private void executeSendPromptToExistingSession(String promptText, List<AttachmentItem> attachments, ApiCallback<String> callback) {
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

        // Jika terdapat lampiran gambar dengan URL base64, tambahkan sebagai input_image
        if (attachments != null) {
            for (AttachmentItem att : attachments) {
                if ("image".equalsIgnoreCase(att.getCategory()) && att.getImageUrl() != null && !att.getImageUrl().isEmpty()) {
                    JsonObject imgObj = new JsonObject();
                    imgObj.addProperty("type", "input_image");
                    imgObj.addProperty("image_url", att.getImageUrl());
                    content.add(imgObj);
                }
            }
        }

        userMsg.add("content", content);
        input.add(userMsg);
        event.add("input", input);

        JsonObject molly = new JsonObject();
        molly.addProperty("mode", "follow_up");
        molly.addProperty("model", getNormalizedModelId());
        molly.addProperty("tool_approval_mode", getNormalizedApprovalMode());

        // Lampirkan ID berkas ke molly.attachment_file_ids sesuai arsitektur Molly Web
        if (attachments != null && !attachments.isEmpty()) {
            JsonArray attIds = new JsonArray();
            for (AttachmentItem att : attachments) {
                if (att.getId() != null && !att.getId().isEmpty()) {
                    attIds.add(att.getId());
                }
            }
            if (attIds.size() > 0) {
                molly.add("attachment_file_ids", attIds);
            }
        }

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
                if (isLocalIp(serverUrl) && !tunnelUrl.isEmpty() && !tunnelUrl.equalsIgnoreCase(serverUrl)) {
                    android.util.Log.w("MollyApiClient", "IP lokal timeout saat kirim prompt ke sesi. Auto-failover ke Tunnel: " + tunnelUrl);
                    updateServerUrl(tunnelUrl);
                    executeSendPromptToExistingSession(promptText, attachments, callback);
                    return;
                }
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
     * Membuat sesi baru di server secara atomik dengan membawa pesan awal pengguna dan lampiran berkas.
     * Menggunakan API standar POST /api/v1/agents/sessions.
     */
    public void createSessionAndSendPrompt(String promptText, List<AttachmentItem> attachments, ApiCallback<String> callback) {
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
                if (isLocalIp(serverUrl) && !tunnelUrl.isEmpty() && !tunnelUrl.equalsIgnoreCase(serverUrl)) {
                    android.util.Log.w("MollyApiClient", "IP lokal timeout saat buat sesi. Auto-failover ke Tunnel: " + tunnelUrl);
                    updateServerUrl(tunnelUrl);
                    createSessionAndSendPrompt(promptText, attachments, callback);
                    return;
                }
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
                            // Jika ada lampiran berkas, kirimkan event lampiran ke sesi baru
                            if (attachments != null && !attachments.isEmpty()) {
                                executeSendPromptToExistingSession(promptText, attachments, callback);
                                return;
                            }
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
                            boolean isPin = false;
                            String model = "";

                            if (s.has("molly") && s.get("molly").isJsonObject()) {
                                JsonObject m = s.getAsJsonObject("molly");
                                if (m.has("title") && !m.get("title").isJsonNull()) {
                                    title = m.get("title").getAsString();
                                }
                                if (m.has("archived")) {
                                    isArch = m.get("archived").getAsBoolean();
                                }
                                if (m.has("is_pinned") && !m.get("is_pinned").isJsonNull()) {
                                    isPin = m.get("is_pinned").getAsBoolean();
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

                            result.add(new ChatSessionItem(id, title, updated, isArch, isPin, "", "Molly Agent", model));
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
    /**
     * Mengambil riwayat percakapan sesi dan mengonversinya menjadi elemen tampilan real
     * (Pesan User, Respon Agen, Tool Machine Execution, dan CoT Thinking).
     * Mendukung auto-pagination jika jumlah item > 100 agar pesan-pesan terbaru (termasuk turn akhir)
     * tidak terpotong dan 100% sinkron dengan Web Molly.
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

        fetchAllSessionElements(sessionId, null, new ArrayList<>(), new ApiCallback<List<JsonElement>>() {
            @Override
            public void onSuccess(List<JsonElement> allElements) {
                List<ChatDisplayItem> list = parseElementsToDisplayList(allElements);
                mainHandler.post(() -> callback.onSuccess(list));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    private void fetchAllSessionElements(String targetSessionId, String afterId, List<JsonElement> accumulated, ApiCallback<List<JsonElement>> callback) {
        String endpoint = serverUrl + "/api/v1/agents/sessions/" + targetSessionId + "/items?limit=100&order=asc";
        if (afterId != null && !afterId.trim().isEmpty()) {
            endpoint += "&after=" + afterId.trim();
        }

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!accumulated.isEmpty()) {
                    callback.onSuccess(accumulated);
                } else {
                    callback.onError(e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 404) {
                    callback.onSuccess(accumulated);
                    return;
                }
                if (!response.isSuccessful()) {
                    if (!accumulated.isEmpty()) {
                        callback.onSuccess(accumulated);
                    } else {
                        callback.onError("Error: " + response.code());
                    }
                    return;
                }

                String bodyStr = response.body() != null ? response.body().string() : "{}";
                try {
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    JsonArray data = obj.has("data") && obj.get("data").isJsonArray() ? obj.getAsJsonArray("data") : null;
                    if (data != null) {
                        for (JsonElement el : data) {
                            accumulated.add(el);
                        }
                    }

                    boolean hasMore = obj.has("has_more") && obj.get("has_more").isJsonPrimitive() && obj.get("has_more").getAsBoolean();
                    String lastId = null;
                    if (obj.has("last_id") && !obj.get("last_id").isJsonNull()) {
                        lastId = obj.get("last_id").getAsString();
                    } else if (data != null && data.size() > 0) {
                        JsonObject lastObj = data.get(data.size() - 1).getAsJsonObject();
                        if (lastObj.has("id")) {
                            lastId = lastObj.get("id").getAsString();
                        }
                    }

                    if (hasMore && lastId != null && !lastId.trim().isEmpty() && accumulated.size() < 1000) {
                        fetchAllSessionElements(targetSessionId, lastId, accumulated, callback);
                    } else {
                        callback.onSuccess(accumulated);
                    }
                } catch (Exception e) {
                    if (!accumulated.isEmpty()) {
                        callback.onSuccess(accumulated);
                    } else {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            }
        });
    }

    public List<ChatDisplayItem> parseItemsToDisplayList(String jsonStr) {
        List<JsonElement> elements = new ArrayList<>();
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonArray data = obj.has("data") && obj.get("data").isJsonArray() ? obj.getAsJsonArray("data") : null;
            if (data != null) {
                for (JsonElement el : data) elements.add(el);
            }
        } catch (Exception ignored) {}
        return parseElementsToDisplayList(elements);
    }

    public List<ChatDisplayItem> parseElementsToDisplayList(List<JsonElement> data) {
        List<ChatDisplayItem> list = new ArrayList<>();
        if (data == null) return list;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Map<String, ChatDisplayItem.SubToolCall> subToolCallsMap = new HashMap<>();
            ChatDisplayItem currentToolGroup = null;
            List<ArtifactItem> pendingArtifacts = new ArrayList<>();

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
                    currentToolGroup = null;
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

                    if (item.has("arguments")) {
                        JsonElement argEl = item.get("arguments");
                        if (argEl.isJsonObject() || argEl.isJsonArray()) {
                            args = argEl.toString();
                        } else if (argEl.isJsonPrimitive()) {
                            args = argEl.getAsString();
                        }
                    } else if (item.has("args")) {
                        JsonElement argEl = item.get("args");
                        if (argEl.isJsonObject() || argEl.isJsonArray()) {
                            args = argEl.toString();
                        } else if (argEl.isJsonPrimitive()) {
                            args = argEl.getAsString();
                        }
                    } else if (item.has("function") && item.getAsJsonObject("function").has("arguments")) {
                        JsonElement argEl = item.getAsJsonObject("function").get("arguments");
                        if (argEl.isJsonObject() || argEl.isJsonArray()) {
                            args = argEl.toString();
                        } else if (argEl.isJsonPrimitive()) {
                            args = argEl.getAsString();
                        }
                    }

                    // Deteksi Artefak dari present_artifacts atau pembuatan berkas HTML/projek
                    if ("present_artifacts".equalsIgnoreCase(toolId)) {
                        try {
                            if (!args.isEmpty()) {
                                JsonElement argsEl = JsonParser.parseString(args);
                                if (argsEl.isJsonObject()) {
                                    JsonObject argsObj = argsEl.getAsJsonObject();
                                    JsonArray arr = null;
                                    if (argsObj.has("filepaths") && argsObj.get("filepaths").isJsonArray()) {
                                        arr = argsObj.getAsJsonArray("filepaths");
                                    } else if (argsObj.has("files") && argsObj.get("files").isJsonArray()) {
                                        arr = argsObj.getAsJsonArray("files");
                                    } else if (argsObj.has("artifacts") && argsObj.get("artifacts").isJsonArray()) {
                                        arr = argsObj.getAsJsonArray("artifacts");
                                    }
                                    if (arr != null) {
                                        for (JsonElement pEl : arr) {
                                            String p = pEl.getAsString().trim();
                                            if (!p.isEmpty()) {
                                                addPendingArtifact(pendingArtifacts, p, sessionId);
                                            }
                                        }
                                    } else if (argsObj.has("file_path")) {
                                        String p = argsObj.get("file_path").getAsString().trim();
                                        if (!p.isEmpty()) addPendingArtifact(pendingArtifacts, p, sessionId);
                                    } else if (argsObj.has("path")) {
                                        String p = argsObj.get("path").getAsString().trim();
                                        if (!p.isEmpty()) addPendingArtifact(pendingArtifacts, p, sessionId);
                                    }
                                } else if (argsEl.isJsonArray()) {
                                    for (JsonElement pEl : argsEl.getAsJsonArray()) {
                                        String p = pEl.getAsString().trim();
                                        if (!p.isEmpty()) {
                                            addPendingArtifact(pendingArtifacts, p, sessionId);
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                        // Sembunyikan present_artifacts dari daftar eksekusi alat mentah (sama seperti Web)
                        continue;
                    }

                    if ("write_file".equalsIgnoreCase(toolId) || "create_file".equalsIgnoreCase(toolId) || "save_file".equalsIgnoreCase(toolId) || "edit_file".equalsIgnoreCase(toolId)) {
                        try {
                            if (!args.isEmpty()) {
                                JsonElement argsEl = JsonParser.parseString(args);
                                if (argsEl.isJsonObject()) {
                                    JsonObject argsObj = argsEl.getAsJsonObject();
                                    String fp = "";
                                    if (argsObj.has("file_path")) fp = argsObj.get("file_path").getAsString().trim();
                                    else if (argsObj.has("path")) fp = argsObj.get("path").getAsString().trim();
                                    else if (argsObj.has("filepath")) fp = argsObj.get("filepath").getAsString().trim();
                                    else if (argsObj.has("filename")) fp = argsObj.get("filename").getAsString().trim();
                                    if (!fp.isEmpty() && (fp.endsWith(".html") || fp.endsWith(".htm") || fp.contains("artifact") || fp.endsWith(".md") || fp.endsWith(".json") || fp.endsWith(".png") || fp.endsWith(".jpg") || fp.endsWith(".pdf") || fp.endsWith(".css") || fp.endsWith(".js") || fp.endsWith(".svg"))) {
                                        addPendingArtifact(pendingArtifacts, fp, sessionId);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
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

                    String callIdKey = item.has("call_id") ? item.get("call_id").getAsString() : itemId;

                    ChatDisplayItem.SubToolCall sub = new ChatDisplayItem.SubToolCall(
                            itemId,
                            toolId,
                            ToolNameHelper.getDisplayName(toolId),
                            args,
                            output,
                            status,
                            turnId,
                            waitpointId
                    );
                    subToolCallsMap.put(callIdKey, sub);
                    subToolCallsMap.put(itemId, sub);

                    if (currentToolGroup == null || (!list.isEmpty() && list.get(list.size() - 1) != currentToolGroup)) {
                        currentToolGroup = ChatDisplayItem.createToolGroup(itemId + "_group", timeStr, turnId);
                        list.add(currentToolGroup);
                    }
                    currentToolGroup.addSubTool(sub);
                }
                // Kasus 3: Hasil Pemanggilan Alat Mesin (Function Call Output)
                else if ("function_call_output".equalsIgnoreCase(type) || "tool_result".equalsIgnoreCase(type)) {
                    String callId = item.has("call_id") ? item.get("call_id").getAsString() : itemId;
                    String outputContent = "";
                    if (item.has("content")) {
                        outputContent = item.get("content").isJsonPrimitive() ? item.get("content").getAsString() : item.get("content").toString();
                    } else if (item.has("output")) {
                        outputContent = item.get("output").isJsonPrimitive() ? item.get("output").getAsString() : item.get("output").toString();
                    }

                    if (subToolCallsMap.containsKey(callId)) {
                        ChatDisplayItem.SubToolCall existing = subToolCallsMap.get(callId);
                        if (existing != null) {
                            existing.output = outputContent;
                            if (item.has("status")) {
                                existing.status = item.get("status").getAsString();
                            } else {
                                existing.status = "completed";
                            }
                        }
                    } else if (currentToolGroup != null) {
                        ChatDisplayItem.SubToolCall sub = new ChatDisplayItem.SubToolCall(
                                itemId,
                                "tool_result",
                                "Hasil Eksekusi",
                                "",
                                outputContent,
                                "completed",
                                turnId,
                                null
                        );
                        currentToolGroup.addSubTool(sub);
                        subToolCallsMap.put(callId, sub);
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
                    currentToolGroup = null;
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
                    ChatDisplayItem agentItem = null;
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
                                agentItem = new ChatDisplayItem(itemId, ChatDisplayItem.TYPE_AGENT, "agent", remainingText, timeStr);
                                list.add(agentItem);
                            }
                        } else if (rawText.startsWith("<think>")) {
                            // Masih sedang berpikir (streaming)
                            String thinkingContent = rawText.substring(7).trim();
                            list.add(ChatDisplayItem.createThinking(itemId + "_think", thinkingContent, timeStr));
                        } else {
                            agentItem = new ChatDisplayItem(itemId, ChatDisplayItem.TYPE_AGENT, "agent", rawText, timeStr);
                            list.add(agentItem);
                        }
                    }

                    // Tempelkan kartu artefak yang dihasilkan pada giliran agen ini
                    if (agentItem != null && !pendingArtifacts.isEmpty()) {
                        agentItem.setArtifacts(new ArrayList<>(pendingArtifacts));
                        pendingArtifacts.clear();
                    }

                    // Fallback Cerdas: Deteksi berkas artefak yang disebutkan di pesan agen jika pendingArtifacts kosong
                    if (agentItem != null && !agentItem.hasArtifacts() && !rawText.isEmpty()) {
                        java.util.regex.Pattern filePattern = java.util.regex.Pattern.compile("(?:`|'|\"|\\b)([a-zA-Z0-9_\\-./]+\\.(html|htm|pdf|docx|xlsx|png|jpg|jpeg|svg|json|md|py|js|css|txt|csv))(?:`|'|\"|\\b)", java.util.regex.Pattern.CASE_INSENSITIVE);
                        java.util.regex.Matcher matcher = filePattern.matcher(rawText);
                        java.util.Set<String> detectedFiles = new java.util.HashSet<>();
                        while (matcher.find()) {
                            String fname = matcher.group(1);
                            if (fname != null && !fname.trim().isEmpty()) {
                                fname = fname.trim();
                                String lower = fname.toLowerCase();
                                if (!lower.startsWith("http") && !lower.contains("github.com") && !lower.contains("google.com") && !lower.endsWith(".com") && !lower.endsWith(".org") && !lower.endsWith(".net")) {
                                    if (!detectedFiles.contains(lower)) {
                                        detectedFiles.add(lower);
                                        agentItem.addArtifact(new ArtifactItem(fname, sessionId));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Jika masih ada sisa artefak di akhir giliran, tautkan ke item agen terakhir
            if (!pendingArtifacts.isEmpty()) {
                boolean attached = false;
                for (int i = list.size() - 1; i >= 0; i--) {
                    ChatDisplayItem it = list.get(i);
                    if (it.getItemType() == ChatDisplayItem.TYPE_AGENT) {
                        for (ArtifactItem art : pendingArtifacts) {
                            it.addArtifact(art);
                        }
                        attached = true;
                        break;
                    }
                }
                if (!attached) {
                    ChatDisplayItem standalone = new ChatDisplayItem(UUID.randomUUID().toString(), ChatDisplayItem.TYPE_AGENT, "agent", "", sdf.format(new Date()));
                    standalone.setArtifacts(new ArrayList<>(pendingArtifacts));
                    list.add(standalone);
                }
                pendingArtifacts.clear();
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
     * Membatalkan putaran agen (turn) yang sedang berlangsung / streaming di server Molly.
     */
    public void cancelCurrentTurn(String turnId, ApiCallback<Boolean> callback) {
        if (!isConfigured() || sessionId == null || sessionId.trim().isEmpty()) {
            if (callback != null) callback.onError("Sesi tidak aktif");
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + sessionId + "/events";
        String idempotencyKey = UUID.randomUUID().toString();

        JsonObject root = new JsonObject();
        JsonArray events = new JsonArray();

        JsonObject ev = new JsonObject();
        ev.addProperty("type", "agent.session.input.cancel");

        JsonObject cancelOpts = new JsonObject();
        if (turnId != null && !turnId.isEmpty()) {
            cancelOpts.addProperty("turn_id", turnId);
        }
        ev.add("molly", cancelOpts);

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
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                boolean ok = response.isSuccessful();
                if (callback != null) {
                    mainHandler.post(() -> {
                        if (ok) callback.onSuccess(true);
                        else callback.onError("Status " + response.code());
                    });
                }
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

    /**
     * Memperbarui metadata sesi (Rename judul atau Toggle Pin to top) di backend Molly.
     * Endpoint: POST /api/v1/agents/sessions/{sessionId}
     */
    public void updateSession(String targetSessionId, String newTitle, Boolean isPinned, ApiCallback<Boolean> callback) {
        if (!isConfigured() || targetSessionId == null || targetSessionId.isEmpty()) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + targetSessionId;
        JsonObject root = new JsonObject();
        JsonObject mollyObj = new JsonObject();
        if (newTitle != null) {
            mollyObj.addProperty("title", newTitle);
        }
        if (isPinned != null) {
            mollyObj.addProperty("is_pinned", isPinned);
        }
        root.add("molly", mollyObj);

        RequestBody body = RequestBody.create(root.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
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
                    else callback.onError("Gagal memperbarui sesi: status " + response.code());
                });
            }
        });
    }

    /**
     * Mengambil status monitoring penggunaan token & konteks sesi aktif dari backend Molly.
     */
    public void fetchContextUsage(ApiCallback<com.molly.agent.model.ContextUsageInfo> callback) {
        if (!isConfigured() || sessionId == null || sessionId.isEmpty() || sessionId.equals("molly-guest-session")) {
            callback.onError("Sesi belum aktif");
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + sessionId + "/state?include_relations=false";
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
                    mainHandler.post(() -> callback.onError("Status: " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    com.molly.agent.model.ContextUsageInfo info = com.molly.agent.model.ContextUsageInfo.parse(obj);
                    mainHandler.post(() -> callback.onSuccess(info));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse context usage: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Meminta server Molly untuk mengompresi konteks riwayat percakapan.
     */
    public void compressContext(ApiCallback<Boolean> callback) {
        if (!isConfigured() || sessionId == null || sessionId.isEmpty()) {
            callback.onError("Sesi belum aktif");
            return;
        }

        String endpoint = serverUrl + "/api/v1/agents/sessions/" + sessionId + "/compress";
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
                    else callback.onError("Gagal kompresi: status " + response.code());
                });
            }
        });
    }

    // =========================================================================
    // Scheduled Tasks API (/api/scheduled-tasks) - Task Scheduler
    // =========================================================================

    public void fetchScheduledTasks(ApiCallback<List<com.molly.agent.model.ScheduledTaskItem>> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum dikonfigurasi");
            return;
        }

        String endpoint = serverUrl + "/api/scheduled-tasks";
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
                    mainHandler.post(() -> callback.onError("Status: " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<com.molly.agent.model.ScheduledTaskItem> list = new ArrayList<>();
                    if (obj.has("jobs") && obj.get("jobs").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("jobs")) {
                            if (el.isJsonObject()) {
                                list.add(com.molly.agent.model.ScheduledTaskItem.fromJson(el.getAsJsonObject()));
                            }
                        }
                    }
                    mainHandler.post(() -> callback.onSuccess(list));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse tugas terjadwal: " + e.getMessage()));
                }
            }
        });
    }

    public void createScheduledTask(JsonObject payload, ApiCallback<com.molly.agent.model.ScheduledTaskItem> callback) {
        com.molly.agent.model.ScheduledTaskItem item = com.molly.agent.model.ScheduledTaskItem.fromJson(payload);
        createScheduledTask(item, callback);
    }

    public void createScheduledTask(com.molly.agent.model.ScheduledTaskItem item, ApiCallback<com.molly.agent.model.ScheduledTaskItem> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum dikonfigurasi");
            return;
        }

        // Dapatkan project id valid terlebih dahulu jika kosong
        if (item.projectId == null || item.projectId.trim().isEmpty() || item.projectId.equals("__auto__")) {
            fetchFirstProjectId(new ApiCallback<String>() {
                @Override
                public void onSuccess(String foundProjectId) {
                    item.projectId = foundProjectId;
                    executeCreateScheduledTask(item, callback);
                }

                @Override
                public void onError(String error) {
                    item.projectId = "default";
                    executeCreateScheduledTask(item, callback);
                }
            });
            return;
        }

        executeCreateScheduledTask(item, callback);
    }

    private void executeCreateScheduledTask(com.molly.agent.model.ScheduledTaskItem item, ApiCallback<com.molly.agent.model.ScheduledTaskItem> callback) {
        String endpoint = serverUrl + "/api/scheduled-tasks";

        JsonObject bodyObj = new JsonObject();
        bodyObj.addProperty("request_id", UUID.randomUUID().toString());
        bodyObj.addProperty("name", item.name != null ? item.name.trim() : "Tugas Terjadwal Baru");
        bodyObj.addProperty("project_id", item.projectId);
        bodyObj.addProperty("agent_slug", item.agentSlug != null ? item.agentSlug : agentId);
        bodyObj.addProperty("prompt", item.prompt != null ? item.prompt.trim() : "");
        bodyObj.addProperty("cron_expression", item.cronExpression != null ? item.cronExpression : "0 9 * * *");
        bodyObj.addProperty("timezone", item.timezone != null ? item.timezone : "Asia/Jakarta");
        bodyObj.addProperty("tool_approval_mode", item.toolApprovalMode != null ? item.toolApprovalMode : "always_trust");
        if (item.modelSpec != null && !item.modelSpec.isEmpty()) {
            bodyObj.addProperty("model_spec", item.modelSpec);
        }
        bodyObj.addProperty("enabled", item.enabled);

        RequestBody body = RequestBody.create(bodyObj.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
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
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Gagal membuat tugas (" + response.code() + "): " + err));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    com.molly.agent.model.ScheduledTaskItem created = com.molly.agent.model.ScheduledTaskItem.fromJson(obj);
                    mainHandler.post(() -> callback.onSuccess(created));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse respon: " + e.getMessage()));
                }
            }
        });
    }

    public void updateScheduledTask(String jobId, JsonObject patch, ApiCallback<com.molly.agent.model.ScheduledTaskItem> callback) {
        if (!isConfigured() || jobId == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/scheduled-tasks/" + jobId;
        RequestBody body = RequestBody.create(patch.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .patch(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Gagal update tugas: status " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    com.molly.agent.model.ScheduledTaskItem updated = com.molly.agent.model.ScheduledTaskItem.fromJson(obj);
                    mainHandler.post(() -> callback.onSuccess(updated));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse update: " + e.getMessage()));
                }
            }
        });
    }

    public void runScheduledTaskNow(String jobId, ApiCallback<com.molly.agent.model.ScheduledRunItem> callback) {
        if (!isConfigured() || jobId == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/scheduled-tasks/" + jobId + "/run-now";
        JsonObject req = new JsonObject();
        req.addProperty("request_id", UUID.randomUUID().toString());

        RequestBody body = RequestBody.create(req.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
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
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Gagal run-now (" + response.code() + "): " + err));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    com.molly.agent.model.ScheduledRunItem run = com.molly.agent.model.ScheduledRunItem.fromJson(obj);
                    mainHandler.post(() -> callback.onSuccess(run));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse respon run: " + e.getMessage()));
                }
            }
        });
    }

    public void deleteScheduledTask(String jobId, ApiCallback<Boolean> callback) {
        if (!isConfigured() || jobId == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/scheduled-tasks/" + jobId;
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .delete()
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
                    else callback.onError("Gagal menghapus tugas: status " + response.code());
                });
            }
        });
    }

    public void fetchScheduledTaskHistory(String jobId, ApiCallback<List<com.molly.agent.model.ScheduledRunItem>> callback) {
        fetchScheduledTaskHistory(jobId, 50, 0, callback);
    }

    public void fetchScheduledTaskHistory(String jobId, int limit, int offset, ApiCallback<List<com.molly.agent.model.ScheduledRunItem>> callback) {
        if (!isConfigured() || jobId == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/scheduled-tasks/" + jobId + "/runs?limit=" + limit + "&offset=" + offset;
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
                    mainHandler.post(() -> callback.onError("Gagal memuat riwayat: status " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<com.molly.agent.model.ScheduledRunItem> runs = new ArrayList<>();
                    if (obj.has("items") && obj.get("items").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("items")) {
                            if (el.isJsonObject()) {
                                runs.add(com.molly.agent.model.ScheduledRunItem.fromJson(el.getAsJsonObject()));
                            }
                        }
                    }
                    mainHandler.post(() -> callback.onSuccess(runs));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse riwayat: " + e.getMessage()));
                }
            }
        });
    }

    public void fetchFirstProjectId(ApiCallback<String> callback) {
        String endpoint = serverUrl + "/api/projects";
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
                    mainHandler.post(() -> callback.onError("Status: " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "[]";
                    JsonElement root = JsonParser.parseString(bodyStr);
                    if (root.isJsonArray() && root.getAsJsonArray().size() > 0) {
                        JsonObject first = root.getAsJsonArray().get(0).getAsJsonObject();
                        String pid = first.has("id") ? first.get("id").getAsString() : "default";
                        mainHandler.post(() -> callback.onSuccess(pid));
                        return;
                    }
                    mainHandler.post(() -> callback.onSuccess("default"));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onSuccess("default"));
                }
            }
        });
    }

    // =========================================================================
    // AI Model Providers API (/api/system/model-providers)
    // =========================================================================

    public void fetchModelProviders(ApiCallback<List<com.molly.agent.model.ModelProviderItem>> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/system/model-providers";
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
                    mainHandler.post(() -> callback.onError("Gagal memuat penyedia model: status " + response.code()));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<com.molly.agent.model.ModelProviderItem> list = new ArrayList<>();
                    if (obj.has("data") && obj.get("data").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("data")) {
                            if (el.isJsonObject()) {
                                list.add(com.molly.agent.model.ModelProviderItem.fromJson(el.getAsJsonObject()));
                            }
                        }
                    }
                    mainHandler.post(() -> callback.onSuccess(list));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse data penyedia model: " + e.getMessage()));
                }
            }
        });
    }

    public void updateModelProvider(String providerId, JsonObject payload, ApiCallback<com.molly.agent.model.ModelProviderItem> callback) {
        if (!isConfigured() || providerId == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/system/model-providers/" + providerId;
        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Gagal update penyedia (" + response.code() + "): " + err));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    JsonObject data = obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : obj;
                    com.molly.agent.model.ModelProviderItem item = com.molly.agent.model.ModelProviderItem.fromJson(data);
                    mainHandler.post(() -> callback.onSuccess(item));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse respon: " + e.getMessage()));
                }
            }
        });
    }

    public void createModelProvider(JsonObject payload, ApiCallback<com.molly.agent.model.ModelProviderItem> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/system/model-providers";
        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
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
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Gagal menambah penyedia (" + response.code() + "): " + err));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    JsonObject data = obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : obj;
                    com.molly.agent.model.ModelProviderItem item = com.molly.agent.model.ModelProviderItem.fromJson(data);
                    mainHandler.post(() -> callback.onSuccess(item));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse respon: " + e.getMessage()));
                }
            }
        });
    }

    public void deleteModelProvider(String providerId, ApiCallback<Boolean> callback) {
        if (!isConfigured() || providerId == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/system/model-providers/" + providerId;
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .delete()
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
                    else callback.onError("Gagal menghapus penyedia: status " + response.code());
                });
            }
        });
    }

    public void fetchRemoteModels(String providerId, ApiCallback<List<com.molly.agent.model.ProviderModelItem>> callback) {
        if (!isConfigured() || providerId == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/system/model-providers/" + providerId + "/remote-models";
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
                    String err = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Gagal mengambil model jarak jauh (" + response.code() + "): " + err));
                    return;
                }
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    List<com.molly.agent.model.ProviderModelItem> models = new ArrayList<>();
                    if (obj.has("data") && obj.get("data").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("data")) {
                            if (el.isJsonObject()) {
                                models.add(com.molly.agent.model.ProviderModelItem.fromJson(el.getAsJsonObject()));
                            }
                        }
                    }
                    mainHandler.post(() -> callback.onSuccess(models));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse model jarak jauh: " + e.getMessage()));
                }
            }
        });
    }

    public void testModelStatus(String spec, ApiCallback<JsonObject> callback) {
        if (!isConfigured() || spec == null) {
            callback.onError("Parameter tidak valid");
            return;
        }

        String endpoint = serverUrl + "/api/system/model-providers/models/status?spec=" + spec;
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
                try {
                    String bodyStr = response.body() != null ? response.body().string() : "{}";
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    JsonObject data = obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : obj;
                    mainHandler.post(() -> callback.onSuccess(data));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse respon uji: " + e.getMessage()));
                }
            }
        });
    }

    public void refreshModelCache(ApiCallback<String> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/system/model-providers/models/cache/refresh";
        RequestBody emptyBody = RequestBody.create("{}", MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(emptyBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Gagal refresh cache: status " + response.code()));
                    return;
                }
                mainHandler.post(() -> callback.onSuccess("Cache model berhasil diperbarui!"));
            }
        });
    }

    /**
     * Mengambil daftar berkas dan folder dari ruang kerja (Workspace Files)
     */
    public void fetchWorkspaceFiles(String path, String query, ApiCallback<List<com.molly.agent.model.WorkspaceFileItem>> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String p = path != null ? path.trim() : "/";
        String q = query != null ? query.trim() : "";
        String endpoint = serverUrl + "/api/system/mobile/workspace/files?path=" + java.net.URLEncoder.encode(p) + "&query=" + java.net.URLEncoder.encode(q);
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

                String bodyStr = response.body() != null ? response.body().string() : "{}";
                List<com.molly.agent.model.WorkspaceFileItem> results = new ArrayList<>();
                try {
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    JsonArray arr = null;
                    if (obj.has("entries") && obj.get("entries").isJsonArray()) {
                        arr = obj.getAsJsonArray("entries");
                    } else if (obj.has("files") && obj.get("files").isJsonArray()) {
                        arr = obj.getAsJsonArray("files");
                    }
                    if (arr != null) {
                        for (JsonElement el : arr) {
                            if (!el.isJsonObject()) continue;
                            JsonObject f = el.getAsJsonObject();
                            String name = f.has("name") ? f.get("name").getAsString() : "";
                            String pth = f.has("path") ? f.get("path").getAsString() : "";
                            boolean isDir = (f.has("is_directory") && f.get("is_directory").getAsBoolean()) ||
                                            (f.has("is_dir") && f.get("is_dir").getAsBoolean());
                            long size = f.has("size_bytes") ? f.get("size_bytes").getAsLong() : (f.has("size") ? f.get("size").getAsLong() : 0);
                            String mod = f.has("last_modified") ? f.get("last_modified").getAsString() : "";
                            results.add(new com.molly.agent.model.WorkspaceFileItem(name, pth, isDir, size, mod));
                        }
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Gagal parse berkas: " + e.getMessage()));
                    return;
                }
                mainHandler.post(() -> callback.onSuccess(results));
            }
        });
    }

    public void createWorkspaceFolder(String parentPath, String name, ApiCallback<Boolean> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/system/mobile/workspace/mkdir";
        JsonObject body = new JsonObject();
        body.addProperty("parent_path", parentPath != null ? parentPath : "/");
        body.addProperty("name", name);

        RequestBody reqBody = RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(reqBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Gagal membuat folder: " + response.code()));
                    return;
                }
                mainHandler.post(() -> callback.onSuccess(true));
            }
        });
    }

    public void deleteWorkspaceFiles(List<String> paths, ApiCallback<Boolean> callback) {
        if (!isConfigured()) {
            callback.onError("Server belum terhubung");
            return;
        }

        String endpoint = serverUrl + "/api/system/mobile/workspace/delete";
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        if (paths != null) {
            for (String p : paths) arr.add(p);
        }
        body.add("paths", arr);

        RequestBody reqBody = RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .post(reqBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Gagal menghapus berkas: " + response.code()));
                    return;
                }
                mainHandler.post(() -> callback.onSuccess(true));
            }
        });
    }

    /**
     * Mengambil JSON mentah metadata sistem (skills, agents, models) untuk helper picker.
     */
    public void fetchMetadataJson(ApiCallback<JsonObject> callback) {
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
                String bodyStr = response.body() != null ? response.body().string() : "{}";
                try {
                    JsonObject obj = JsonParser.parseString(bodyStr).getAsJsonObject();
                    mainHandler.post(() -> callback.onSuccess(obj));
                } catch (Exception ex) {
                    mainHandler.post(() -> callback.onError("Parse error: " + ex.getMessage()));
                }
            }
        });
    }

    /**
     * Mengambil konten mentah artefak/berkas (HTML, Markdown, kode, teks) untuk live preview.
     */
    public void fetchArtifactRaw(String artifactPath, ApiCallback<String> callback) {
        String baseUrl = getBaseUrl();
        if (baseUrl.isEmpty() || artifactPath == null || artifactPath.trim().isEmpty()) {
            callback.onError("Server belum terhubung atau path berkas tidak valid");
            return;
        }

        String rawPath = artifactPath.trim();
        // Bersihkan prefix direktori sandbox lokal jika ada
        if (rawPath.startsWith("/home/user/workspace/")) {
            rawPath = rawPath.substring("/home/user/workspace/".length());
        } else if (rawPath.startsWith("home/user/workspace/")) {
            rawPath = rawPath.substring("home/user/workspace/".length());
        }
        if (!rawPath.startsWith("/")) {
            rawPath = "/" + rawPath;
        }

        final String cleanWorkspacePath = rawPath;
        // Jika berkas berada di ruang kerja permanen (saved_artifacts, projects) atau sesi tamu, langsung ambil dari workspace
        boolean isWorkspaceDirect = cleanWorkspacePath.startsWith("/saved_artifacts/")
                || cleanWorkspacePath.startsWith("/projects/")
                || cleanWorkspacePath.startsWith("/workspace/")
                || sessionId == null || sessionId.isEmpty() || sessionId.equals("molly-guest-session");

        if (isWorkspaceDirect) {
            fetchWorkspaceFileContent(cleanWorkspacePath, callback);
            return;
        }

        String relativePath = cleanWorkspacePath.startsWith("/") ? cleanWorkspacePath.substring(1) : cleanWorkspacePath;
        String encodedPath = relativePath;
        try {
            String[] segments = relativePath.split("/");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) sb.append("/");
                sb.append(java.net.URLEncoder.encode(segments[i], "UTF-8").replace("+", "%20"));
            }
            encodedPath = sb.toString();
        } catch (Exception ignored) {}

        String endpoint = baseUrl + "/api/v1/agents/sessions/" + sessionId + "/artifacts/" + encodedPath + "?preview=true";

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                fetchWorkspaceFileContent(cleanWorkspacePath, callback);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String raw = response.body().string();
                    String content = extractCleanContent(raw);
                    mainHandler.post(() -> callback.onSuccess(content));
                } else {
                    fetchWorkspaceFileContent(cleanWorkspacePath, callback);
                }
            }
        });
    }

    private void addPendingArtifact(List<ArtifactItem> list, String path, String sessionId) {
        if (path == null || path.trim().isEmpty() || list == null) return;
        ArtifactItem newItem = new ArtifactItem(path.trim(), sessionId);
        for (int i = 0; i < list.size(); i++) {
            ArtifactItem existing = list.get(i);
            if (existing.getName().equalsIgnoreCase(newItem.getName())) {
                if (newItem.getPath() != null && (existing.getPath() == null || newItem.getPath().length() > existing.getPath().length())) {
                    list.set(i, newItem);
                }
                return;
            }
        }
        list.add(newItem);
    }

    private String extractCleanContent(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonElement el = JsonParser.parseString(trimmed);
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.has("content") && !obj.get("content").isJsonNull()) {
                        return obj.get("content").getAsString();
                    }
                    if (obj.has("data")) {
                        if (obj.get("data").isJsonObject() && obj.getAsJsonObject("data").has("content")) {
                            return obj.getAsJsonObject("data").get("content").getAsString();
                        } else if (obj.get("data").isJsonPrimitive()) {
                            return obj.get("data").getAsString();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return raw;
    }

    private void fetchWorkspaceFileContent(String path, ApiCallback<String> callback) {
        String baseUrl = getBaseUrl();
        if (baseUrl.isEmpty() || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
            mainHandler.post(() -> callback.onError("Server belum terhubung atau URL tidak valid"));
            return;
        }

        String normalizedPath = (path != null && !path.trim().isEmpty()) ? path.trim() : "/";
        if (normalizedPath.startsWith("/home/user/workspace/")) {
            normalizedPath = normalizedPath.substring("/home/user/workspace/".length());
        }
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        executeWorkspaceFileFetch(baseUrl, normalizedPath, false, callback);
    }

    private void executeWorkspaceFileFetch(String baseUrl, String currentPath, boolean hasRetried, ApiCallback<String> callback) {
        try {
            if (baseUrl == null || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
                mainHandler.post(() -> callback.onError("URL Server tidak valid"));
                return;
            }
            String endpoint = baseUrl + "/api/system/mobile/workspace/file?path=" + java.net.URLEncoder.encode(currentPath, "UTF-8");
            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Bypass-Tunnel-Reminder", "true")
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Gagal mengambil file: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        String bodyStr = response.body().string();
                        String clean = extractCleanContent(bodyStr);
                        mainHandler.post(() -> callback.onSuccess(clean));
                    } else if (!hasRetried && response.code() == 404) {
                        // Jika 404, coba alternatif /saved_artifacts/ atau root workspace
                        String fileName = currentPath.contains("/") ? currentPath.substring(currentPath.lastIndexOf('/') + 1) : currentPath;
                        String alternatePath = currentPath.startsWith("/saved_artifacts/") ? ("/" + fileName) : ("/saved_artifacts/" + fileName);
                        executeWorkspaceFileFetch(baseUrl, alternatePath, true, callback);
                    } else {
                        mainHandler.post(() -> callback.onError("Error " + response.code() + " membaca berkas: " + currentPath));
                    }
                }
            });
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
        }
    }

    /**
     * Mengunduh berkas artefak dan menyimpannya ke folder Downloads perangkat.
     */
    public void downloadArtifactFile(String artifactPath, String targetFileName, ApiCallback<File> callback) {
        String baseUrl = getBaseUrl();
        if (baseUrl.isEmpty() || artifactPath == null || artifactPath.trim().isEmpty()) {
            callback.onError("Server belum terhubung atau path berkas tidak valid");
            return;
        }

        String rawPath = artifactPath.trim();
        if (rawPath.startsWith("/home/user/workspace/")) {
            rawPath = rawPath.substring("/home/user/workspace/".length());
        }
        if (!rawPath.startsWith("/")) {
            rawPath = "/" + rawPath;
        }

        final String cleanPath = rawPath;
        boolean isWorkspaceDirect = cleanPath.startsWith("/saved_artifacts/")
                || cleanPath.startsWith("/projects/")
                || cleanPath.startsWith("/workspace/")
                || sessionId == null || sessionId.isEmpty() || sessionId.equals("molly-guest-session");

        if (isWorkspaceDirect) {
            downloadWorkspaceFileDirect(baseUrl, cleanPath, targetFileName, callback);
            return;
        }

        String relativePath = cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath;
        String encodedPath = relativePath;
        try {
            String[] segments = relativePath.split("/");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) sb.append("/");
                sb.append(java.net.URLEncoder.encode(segments[i], "UTF-8").replace("+", "%20"));
            }
            encodedPath = sb.toString();
        } catch (Exception ignored) {}

        String endpoint = baseUrl + "/api/v1/agents/sessions/" + sessionId + "/artifacts/" + encodedPath + "?download=true";

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                downloadWorkspaceFileDirect(baseUrl, cleanPath, targetFileName, callback);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    saveResponseToFile(response, cleanPath, targetFileName, callback);
                } else {
                    downloadWorkspaceFileDirect(baseUrl, cleanPath, targetFileName, callback);
                }
            }
        });
    }

    private void downloadWorkspaceFileDirect(String baseUrl, String workspacePath, String targetFileName, ApiCallback<File> callback) {
        try {
            if (baseUrl == null || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
                mainHandler.post(() -> callback.onError("URL Server tidak valid"));
                return;
            }
            String endpoint = baseUrl + "/api/system/mobile/workspace/download?path=" + java.net.URLEncoder.encode(workspacePath, "UTF-8");
            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Bypass-Tunnel-Reminder", "true")
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Gagal mengunduh berkas: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        saveResponseToFile(response, workspacePath, targetFileName, callback);
                    } else {
                        mainHandler.post(() -> callback.onError("Unduhan gagal dengan status: " + response.code()));
                    }
                }
            });
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
        }
    }

    private void saveResponseToFile(Response response, String artifactPath, String targetFileName, ApiCallback<File> callback) {
        try {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            String finalName = (targetFileName != null && !targetFileName.trim().isEmpty())
                    ? targetFileName : new File(artifactPath).getName();
            File destFile = new File(downloadDir, finalName);

            try (java.io.InputStream is = response.body().byteStream();
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
            }

            mainHandler.post(() -> callback.onSuccess(destFile));
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Gagal menyimpan berkas ke Download: " + e.getMessage()));
        }
    }

    /**
     * Menyimpan artefak thread ke direktori ruang kerja pribadi (personal space).
     */
    public void saveArtifactToWorkspace(String artifactPath, String destinationPath, ApiCallback<Boolean> callback) {
        String baseUrl = getBaseUrl();
        if (baseUrl.isEmpty() || artifactPath == null || artifactPath.isEmpty()) {
            callback.onError("Parameter tidak valid atau server belum terhubung");
            return;
        }

        if (sessionId == null || sessionId.isEmpty() || sessionId.equals("molly-guest-session")) {
            callback.onError("Sesi belum aktif");
            return;
        }

        String endpoint = baseUrl + "/api/v1/agents/sessions/" + sessionId + "/artifacts/save";
        JsonObject bodyObj = new JsonObject();
        bodyObj.addProperty("path", artifactPath);
        bodyObj.addProperty("destination_path", (destinationPath != null && !destinationPath.isEmpty()) ? destinationPath : "/saved_artifacts");

        RequestBody body = RequestBody.create(bodyObj.toString(), JSON);
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Bypass-Tunnel-Reminder", "true")
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
                    mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    String err = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Gagal menyimpan (" + response.code() + "): " + err));
                }
            }
        });
    }
}
