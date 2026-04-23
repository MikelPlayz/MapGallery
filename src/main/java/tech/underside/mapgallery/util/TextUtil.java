package tech.underside.mapgallery.util;

public final class TextUtil {
    private TextUtil() {}

    public static String sanitizeToken(String input, int maxLen) {
        String cleaned = input == null ? "untitled" : input.replaceAll("[^a-zA-Z0-9 _.-]", "").trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        if (cleaned.isBlank()) cleaned = "untitled";
        if (cleaned.length() > maxLen) cleaned = cleaned.substring(0, maxLen);
        return cleaned;
    }
}
