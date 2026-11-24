package com.molly.agent.model;

public class ChatSessionItem {
    private String id;
    private String title;
    private long updatedAt;
    private boolean isArchived;
    private String lastMessage;
    private String agentName;
    private String modelName;

    public ChatSessionItem(String id, String title, long updatedAt, boolean isArchived, String lastMessage, String agentName, String modelName) {
        this.id = id;
        this.title = (title != null && !title.trim().isEmpty()) ? title : "Percakapan Baru";
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
        this.isArchived = isArchived;
        this.lastMessage = lastMessage != null ? lastMessage : "";
        this.agentName = agentName != null ? agentName : "Molly Agent";
        this.modelName = modelName != null ? modelName : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
