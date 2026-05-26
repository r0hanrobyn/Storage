package storage.dto;

public record QuotaResponse(
        long usedBytes,
        long totalBytes,
        int percentUsed,
        String usedFormatted,
        String totalFormatted
) {
    public static QuotaResponse of(long used, long total) {
        int pct = total > 0 ? (int) Math.min(100, (used * 100) / total) : 0;
        return new QuotaResponse(used, total, pct, format(used), format(total));
    }

    private static String format(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
