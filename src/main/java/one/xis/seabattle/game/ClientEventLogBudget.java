package one.xis.seabattle.game;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounds client-originated log traffic and its bookkeeping independently of frame rate. */
final class ClientEventLogBudget {
    private final Map<String, long[]> windows = new LinkedHashMap<>();

    synchronized boolean allow(String player, long now) {
        long[] window = windows.get(player);
        if (window == null) {
            if (windows.size() >= 512) windows.remove(windows.keySet().iterator().next());
            window = new long[]{now, 0};
            windows.put(player, window);
        }
        if (now - window[0] >= 10_000_000_000L) {
            window[0] = now;
            window[1] = 0;
        }
        return ++window[1] <= 40;
    }
}
