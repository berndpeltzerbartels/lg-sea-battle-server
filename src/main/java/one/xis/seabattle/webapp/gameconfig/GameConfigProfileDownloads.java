package one.xis.seabattle.webapp.gameconfig;

import one.xis.context.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, single-use download tickets, issued only by the ADMIN page action. */
@Service
class GameConfigProfileDownloads {
    private static final long LIFETIME_MILLIS = 60_000;
    private record Download(String json, long expiresAt) { }
    private final Map<String, Download> pending = new ConcurrentHashMap<>();

    String prepare(String json) {
        long now = nowMillis();
        pending.values().removeIf(value -> value.expiresAt() <= now);
        String ticket = UUID.randomUUID().toString();
        pending.put(ticket, new Download(json, now + LIFETIME_MILLIS));
        return ticket;
    }

    String take(String ticket) {
        Download download = pending.remove(ticket);
        return download == null || download.expiresAt() <= nowMillis() ? null : download.json();
    }

    long nowMillis() { return System.currentTimeMillis(); }
}
