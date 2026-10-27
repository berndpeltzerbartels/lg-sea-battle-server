package one.xis.seabattle.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Uses an existing bot attack decision; never searches for targets or evaluates bot recipients. */
public final class AircraftAttackWarnings {
    public record Warning(String id, String direction, int count, double expiresAt) {}
    private static final class Approach {
        String target;
        double lastSeen = Double.NEGATIVE_INFINITY;
        double nextCheck;
        boolean announced;
    }
    private final Map<String, Approach> approaches = new HashMap<>();
    private Map<String, Warning> published = Map.of();
    private double nextExpiry;

    Map<String, Warning> snapshot() { return published; }

    void observe(double now, Ship plane, Ship target) {
        Approach approach = approaches.computeIfAbsent(plane.id(), key -> new Approach());
        if (now < approach.nextCheck && target.id().equals(approach.target)) return;
        approach.nextCheck = now + 1;
        if (!target.id().equals(approach.target) || now - approach.lastSeen >= 10) approach.announced = false;
        approach.target = target.id();
        approach.lastSeen = now;
        Warning previous = published.get(target.id());
        if (approach.announced || previous != null && previous.expiresAt() > now) return;
        Map<String, Warning> next = new HashMap<>(published);
        next.put(target.id(), new Warning(UUID.randomUUID().toString(), PeriscopeWarnings.direction(target, plane), 1, now + 6));
        published = Map.copyOf(next);
        approach.announced = true;
    }

    void expire(double now) {
        if (now < nextExpiry) return;
        nextExpiry = now + 1;
        if (!published.isEmpty()) {
            Map<String, Warning> next = new HashMap<>(published);
            next.values().removeIf(warning -> warning.expiresAt() <= now);
            published = Map.copyOf(next);
        }
        approaches.values().removeIf(approach -> now - approach.lastSeen >= 30);
    }
}
