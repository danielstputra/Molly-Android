package com.molly.agent.model;

public class ChatMessage {
    private String id;
    private String sender; // "user" or "agent"
    private String text;
    private String timestamp;
    private boolean isStreaming;

    public ChatMessage(String id, String sender, String text, String timestamp) {
        this.id = id;
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
        this.isStreaming = false;
    }

    public String getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void setStreaming(boolean streaming) {
        isStreaming = streaming;
    }
}
