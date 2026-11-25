package com.molly.agent.model;

public class WorkspaceFileItem {
    public String name;
    public String path;
    public boolean isDirectory;
    public long sizeBytes;
    public String lastModified;

    public WorkspaceFileItem(String name, String path, boolean isDirectory, long sizeBytes, String lastModified) {
        this.name = name != null ? name : "";
        this.path = path != null ? path : "";
        this.isDirectory = isDirectory;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified != null ? lastModified : "";
    }

    public String getFormattedSize() {
        if (isDirectory) return "Folder";
        if (sizeBytes <= 0) return "0 B";
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    public String getIcon() {
        if (isDirectory) return "📁";
        String lower = name.toLowerCase();
        if (lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".java") || lower.endsWith(".html") || lower.endsWith(".css")) {
            return "💻";
        }
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "📝";
        }
        if (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".xml")) {
            return "⚙️";
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg") || lower.endsWith(".webp")) {
            return "🖼️";
        }
        if (lower.endsWith(".pdf")) {
            return "📕";
        }
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")) {
            return "📦";
        }
        return "📄";
    }
}
