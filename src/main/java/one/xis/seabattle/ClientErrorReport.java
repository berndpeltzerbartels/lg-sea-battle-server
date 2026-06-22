package one.xis.seabattle;

public record ClientErrorReport(
        String type,
        String message,
        String source,
        int line,
        int column,
        String stack,
        String url,
        String userAgent,
        String screen,
        String viewport
) {
}
