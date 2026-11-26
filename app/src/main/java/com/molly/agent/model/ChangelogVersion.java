package com.molly.agent.model;

import java.util.ArrayList;
import java.util.List;

public class ChangelogVersion {
    private final int versionCode;
    private final String versionName;
    private final String releaseDate;
    private final String title;
    private final String tag;
    private final List<String> changes;

    public ChangelogVersion(int versionCode, String versionName, String releaseDate, String title, String tag, List<String> changes) {
        this.versionCode = versionCode;
        this.versionName = versionName != null ? versionName : "";
        this.releaseDate = releaseDate != null ? releaseDate : "";
        this.title = title != null ? title : "";
        this.tag = tag != null ? tag : "";
        this.changes = changes != null ? changes : new ArrayList<>();
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public String getTitle() {
        return title;
    }

    public String getTag() {
        return tag;
    }

    public List<String> getChanges() {
        return changes;
    }

    public boolean isCurrentVersion(int installedVersionCode) {
        return this.versionCode == installedVersionCode;
    }
}
