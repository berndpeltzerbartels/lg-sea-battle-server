package one.xis.seabattle.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Only explicit user input renews a connected player's lease. */
final class PlayerActivity {
    static final long IDLE_MILLIS = 5 * 60_000L;
    private final Map<String, Long> lastInput = new HashMap<>();

    void connected(String player, long now) { lastInput.putIfAbsent(player, now); }
    boolean input(String player, long now) {
        Long previous = lastInput.get(player);
        if (previous == null || now - previous >= IDLE_MILLIS) return false;
        lastInput.put(player, now);
        return true;
    }
    List<String> expired(long now) {
        return lastInput.entrySet().stream().filter(e -> now - e.getValue() >= IDLE_MILLIS)
                .map(Map.Entry::getKey).toList();
    }
    void remove(String player) { lastInput.remove(player); }
}
