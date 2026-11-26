package com.molly.agent.model;

import android.net.Uri;

import java.io.Serializable;

/**
 * Model data untuk lampiran berkas di chat Molly Android
 * Mendukung File, Image, Dokumen, Musik/Audio, Video, dan Berkas Ruang Kerja.
 */
public class AttachmentItem implements Serializable {
    private String id;              // ID file dari server (file_id)
    private String fileName;        // Nama berkas asli
    private long fileSize;          // Ukuran dalam bytes
    private String mimeType;        // MIME type (image/png, application/pdf, audio/mp3, video/mp4, dll)
    private String category;        // "image", "document", "audio", "video", "file"
    private String localUriString;  // Uri string lokal perangkat
    private String imageUrl;        // URL data base64 atau remote URL jika gambar
    private String thumbnailUrl;   // URL thumbnail gambar jika ada
    private boolean isUploading;    // Flag sedang diunggah
    private boolean isUploaded;     // Flag berhasil diunggah
    private String errorMessage;    // Pesan error jika gagal

    public AttachmentItem() {
    }

    public AttachmentItem(String fileName, long fileSize, String mimeType, String category, String localUriString) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.category = category;
        this.localUriString = localUriString;
        this.isUploading = false;
        this.isUploaded = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName != null ? fileName : "Berkas";
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getCategory() {
        return category != null ? category : "file";
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocalUriString() {
        return localUriString;
    }

    public void setLocalUriString(String localUriString) {
        this.localUriString = localUriString;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public boolean isUploading() {
        return isUploading;
    }

    public void setUploading(boolean uploading) {
        isUploading = uploading;
    }

    public boolean isUploaded() {
        return isUploaded;
    }

    public void setUploaded(boolean uploaded) {
        isUploaded = uploaded;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFormattedSize() {
        if (fileSize <= 0) return "";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }

    public String getCategoryEmoji() {
        if ("image".equalsIgnoreCase(category)) return "📷";
        if ("audio".equalsIgnoreCase(category)) return "🎵";
        if ("video".equalsIgnoreCase(category)) return "🎬";
        if ("document".equalsIgnoreCase(category)) return "📄";
        return "📎";
    }
}
