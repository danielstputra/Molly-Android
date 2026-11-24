package com.molly.agent.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionHistoryManager {
    private static final String PREF_NAME = "molly_sessions_cache";
    private static final String KEY_SESSIONS = "cached_sessions_list";

    private final SharedPreferences prefs;
    private final Gson gson;

    public SessionHistoryManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public synchronized List<ChatSessionItem> getSessions(boolean archived) {
        String json = prefs.getString(KEY_SESSIONS, "[]");
        Type type = new TypeToken<List<ChatSessionItem>>() {}.getType();
        List<ChatSessionItem> all = gson.fromJson(json, type);
        if (all == null) all = new ArrayList<>();

        List<ChatSessionItem> filtered = new ArrayList<>();
        for (ChatSessionItem item : all) {
            if (item.isArchived() == archived) {
                filtered.add(item);
            }
        }
        // Urutkan dari yang terbaru (updatedAt descending)
        Collections.sort(filtered, (a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return filtered;
    }

    public synchronized void recordOrUpdateSession(String sessionId, String title, String lastMsg, String agentName, String modelName) {
        if (sessionId == null || sessionId.trim().isEmpty()) return;

        String json = prefs.getString(KEY_SESSIONS, "[]");
        Type type = new TypeToken<List<ChatSessionItem>>() {}.getType();
        List<ChatSessionItem> all = gson.fromJson(json, type);
        if (all == null) all = new ArrayList<>();

        ChatSessionItem target = null;
        for (ChatSessionItem item : all) {
            if (item.getId().equals(sessionId)) {
                target = item;
                break;
            }
        }

        long now = System.currentTimeMillis();
        if (target != null) {
            if (title != null && !title.trim().isEmpty() && !title.equals("Percakapan Baru")) {
                target.setTitle(title);
            }
            if (lastMsg != null && !lastMsg.trim().isEmpty()) {
                target.setLastMessage(lastMsg);
            }
            if (agentName != null) target.setAgentName(agentName);
            if (modelName != null) target.setModelName(modelName);
            target.setUpdatedAt(now);
        } else {
            String initialTitle = (title != null && !title.trim().isEmpty()) ? title : "Percakapan Baru";
            ChatSessionItem newItem = new ChatSessionItem(sessionId, initialTitle, now, false, lastMsg, agentName, modelName);
            all.add(0, newItem);
        }

        saveAll(all);
    }

    public synchronized void setArchived(String sessionId, boolean archived) {
        String json = prefs.getString(KEY_SESSIONS, "[]");
        Type type = new TypeToken<List<ChatSessionItem>>() {}.getType();
        List<ChatSessionItem> all = gson.fromJson(json, type);
        if (all == null) return;

        for (ChatSessionItem item : all) {
            if (item.getId().equals(sessionId)) {
                item.setArchived(archived);
                item.setUpdatedAt(System.currentTimeMillis());
                break;
            }
        }
        saveAll(all);
    }

    public synchronized void deleteSession(String sessionId) {
        String json = prefs.getString(KEY_SESSIONS, "[]");
        Type type = new TypeToken<List<ChatSessionItem>>() {}.getType();
        List<ChatSessionItem> all = gson.fromJson(json, type);
        if (all == null) return;

        List<ChatSessionItem> updated = new ArrayList<>();
        for (ChatSessionItem item : all) {
            if (!item.getId().equals(sessionId)) {
                updated.add(item);
            }
        }
        saveAll(updated);
    }

    public synchronized void mergeServerSessions(List<ChatSessionItem> serverSessions) {
        if (serverSessions == null || serverSessions.isEmpty()) return;

        String json = prefs.getString(KEY_SESSIONS, "[]");
        Type type = new TypeToken<List<ChatSessionItem>>() {}.getType();
        List<ChatSessionItem> local = gson.fromJson(json, type);
        if (local == null) local = new ArrayList<>();

        for (ChatSessionItem s : serverSessions) {
            boolean exists = false;
            for (ChatSessionItem l : local) {
                if (l.getId().equals(s.getId())) {
                    exists = true;
                    // Update metadata
                    if (s.getTitle() != null && !s.getTitle().isEmpty()) l.setTitle(s.getTitle());
                    l.setArchived(s.isArchived());
                    break;
                }
            }
            if (!exists) {
                local.add(s);
            }
        }
        saveAll(local);
    }

    private void saveAll(List<ChatSessionItem> list) {
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(list)).apply();
    }
}
