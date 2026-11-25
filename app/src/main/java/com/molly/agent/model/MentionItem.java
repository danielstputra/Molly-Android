package com.molly.agent.model;

public class MentionItem {
    public static final String TYPE_SKILL = "skill";
    public static final String TYPE_AGENT = "agent";
    public static final String TYPE_FILE = "file";

    public String id;
    public String name;
    public String description;
    public String type; // "skill", "agent", "file"
    public String icon;
    public boolean showGroupHeader = false;
    public String groupHeaderTitle = "";

    public MentionItem(String id, String name, String description, String type, String icon) {
        this.id = id;
        this.name = name;
        this.description = description != null ? description : "";
        this.type = type != null ? type : TYPE_SKILL;
        this.icon = icon != null ? icon : (TYPE_AGENT.equals(type) ? "🤖" : "🤹");
    }
}
