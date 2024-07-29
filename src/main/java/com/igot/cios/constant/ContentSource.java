package com.igot.cios.constant;

public enum ContentSource {
    CORNELL("/Transformation/cornell.json"),
    UPGRAD("/Transformation/upgrad.json");

    private final String filePath;

    ContentSource(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public static ContentSource fromProviderName(String providerName) {
        switch (providerName.toLowerCase()) {
            case "cornell":
                return CORNELL;
            case "upgrad":
                return UPGRAD;
            default:
                throw new RuntimeException("Unknown provider name: " + providerName);
        }
    }
}