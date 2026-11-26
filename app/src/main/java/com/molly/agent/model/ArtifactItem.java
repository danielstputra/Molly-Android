package com.molly.agent.model;

import java.io.Serializable;

public class ArtifactItem implements Serializable {
    private String path;
    private String name;
    private String extension;
    private String threadId;

    public ArtifactItem(String path, String threadId) {
        this.path = path != null ? path.trim() : "";
        this.threadId = threadId;
        
        String cleanPath = this.path;
        if (cleanPath.contains("/")) {
            this.name = cleanPath.substring(cleanPath.lastIndexOf('/') + 1);
        } else {
            this.name = cleanPath;
        }

        if (this.name.contains(".")) {
            this.extension = this.name.substring(this.name.lastIndexOf('.') + 1).toUpperCase();
        } else {
            this.extension = "FILE";
        }
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return (name != null && !name.isEmpty()) ? name : "artifact";
    }

    public String getExtension() {
        return (extension != null && !extension.isEmpty()) ? extension : "FILE";
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public boolean isHtml() {
        return "HTML".equalsIgnoreCase(extension) || "HTM".equalsIgnoreCase(extension);
    }

    public boolean isImage() {
        return "PNG".equalsIgnoreCase(extension) || "JPG".equalsIgnoreCase(extension) 
                || "JPEG".equalsIgnoreCase(extension) || "WEBP".equalsIgnoreCase(extension) 
                || "SVG".equalsIgnoreCase(extension) || "GIF".equalsIgnoreCase(extension);
    }

    public String getMetaLabel() {
        return "Artifact File · " + getExtension();
    }
}
