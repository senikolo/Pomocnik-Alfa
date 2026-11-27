package com.ispina.lokalnie;

import java.util.Locale;

final class PARecordingProgress {
    private static final Locale POLISH = Locale.forLanguageTag("pl-PL");

    final long milliseconds;
    final long bytes;
    final String extension;

    PARecordingProgress(long milliseconds, long bytes, String extension) {
        this.milliseconds = milliseconds;
        this.bytes = bytes;
        this.extension = extension;
    }

    String details() {
        long seconds = milliseconds / 1000;
        String time = seconds >= 3600
            ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        String format = extension == null ? "Format w trakcie rozpoznawania" : extension.toUpperCase(Locale.ROOT);
        return time + "  ·  " + size() + "  ·  " + format;
    }

    private String size() {
        if (bytes < 1000) return bytes + " B";
        if (bytes < 1_000_000) return String.format(POLISH, "%.1f kB", bytes / 1000.0);
        if (bytes < 1_000_000_000) return String.format(POLISH, "%.1f MB", bytes / 1_000_000.0);
        return String.format(POLISH, "%.1f GB", bytes / 1_000_000_000.0);
    }
}
