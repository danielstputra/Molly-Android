package com.molly.agent.model;

public class AppUpdateInfo {
    public boolean hasUpdate;
    public int latestVersionCode;
    public String latestVersionName;
    public String downloadUrl;
    public String changelog;
    public boolean forceUpdate;
    public long fileSize;

    public AppUpdateInfo(boolean hasUpdate, int latestVersionCode, String latestVersionName, String downloadUrl, String changelog, boolean forceUpdate, long fileSize) {
        this.hasUpdate = hasUpdate;
        this.latestVersionCode = latestVersionCode;
        this.latestVersionName = latestVersionName != null ? latestVersionName : "";
        this.downloadUrl = downloadUrl != null ? downloadUrl : "";
        this.changelog = changelog != null ? changelog : "";
        this.forceUpdate = forceUpdate;
        this.fileSize = fileSize;
    }
}
