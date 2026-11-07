package one.xis.seabattle.webapp.gameconfig;

import one.xis.context.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, single-use download tickets, issued only by the ADMIN page action. */
@Service
class GameConfigProfileDownloads {
    private static final long LIFETIME_MILLIS = 60_000;
    record Download(String json, String fileName, long expiresAt) { }
    private final Map<String, Download> pending = new ConcurrentHashMap<>();

    String prepare(String profileName, String json) {
        long now = nowMillis();
        pending.values().removeIf(value -> value.expiresAt() <= now);
        String ticket = UUID.randomUUID().toString();
        pending.put(ticket, new Download(json, fileName(profileName), now + LIFETIME_MILLIS));
        return ticket;
    }

    Download take(String ticket) {
        Download download = pending.remove(ticket);
        return download == null || download.expiresAt() <= nowMillis() ? null : download;
    }

    static String fileName(String profileName) {
        String name = profileName == null ? "" : profileName.strip();
        name = name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").replaceAll("[. ]+$", "");
        if (name.isBlank()) name = "Spielprofil";
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".json") ? name : name + ".json";
    }

    long nowMillis() { return System.currentTimeMillis(); }
}
