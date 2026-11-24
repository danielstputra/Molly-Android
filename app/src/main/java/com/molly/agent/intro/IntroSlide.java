package com.molly.agent.intro;

public class IntroSlide {
    private final int illustrationRes;
    private final String tagline;
    private final String title;
    private final String description;

    public IntroSlide(int illustrationRes, String tagline, String title, String description) {
        this.illustrationRes = illustrationRes;
        this.tagline = tagline;
        this.title = title;
        this.description = description;
    }

    public int getIllustrationRes() {
        return illustrationRes;
    }

    public String getTagline() {
        return tagline;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
