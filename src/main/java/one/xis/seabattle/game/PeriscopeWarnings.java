package one.xis.seabattle.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ship-wide sightings for human crews; independent of occupied stations or target controller. */
public final class PeriscopeWarnings {
    // Effective periscope depth starts 0.08 model units before the settled depth (main.js).
    private static final double PERISCOPE_BAND_TOP = -(0.26 + 1.52) * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private static final double CONTACT_RESET_SECONDS = 10;
    private static final double NOTICE_SECONDS = 6;
    private static final String[] DIRECTIONS = {"voraus", "Steuerbord voraus", "Steuerbord", "Steuerbord achtern",
            "achtern", "Backbord achtern", "Backbord", "Backbord voraus"};
    private final Map<String, Map<String, Contact>> contactsByObserver = new HashMap<>();
    private Map<String, Warning> published = Map.of();
    private double nextCheck;

    public record Warning(String id, String direction, int count, double expiresAt) {}
    private static final class Contact {
        double lastSeen;
        boolean announced;
        Contact(double now) { lastSeen = now; }
    }

    Map<String, Warning> snapshot() { return published; }

    void update(double now, List<Ship> humanShips, RadarService.VisibilityCache visibility, WorldMap world) {
        if (now < nextCheck) return;
        nextCheck = now + 1;
        if (humanShips.isEmpty()) {
            contactsByObserver.clear();
            published = Map.of();
            return;
        }
        Map<String, Warning> next = new HashMap<>();
        Set<String> observers = new HashSet<>();
        for (Ship observer : humanShips) {
            if (!"active".equals(observer.state()) || observer.isScoutPlane()
                    || observer.isSubmarine() && observer.y() + SubmarinePeriscopeGeometry.topAboveRoot(observer.y()) <= 0) continue;
            observers.add(observer.id());
            Map<String, Contact> contacts = contactsByObserver.computeIfAbsent(observer.id(), key -> new HashMap<>());
            contacts.values().removeIf(contact -> now - contact.lastSeen >= CONTACT_RESET_SECONDS);
            Warning previous = published.get(observer.id());
            if (previous != null && previous.expiresAt() > now) next.put(observer.id(), previous);
            Ship nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            int newContacts = 0;
            // Same index used by bot decisions; cheap filters precede terrain intersection.
            for (Ship target : visibility.candidates(observer, 300)) {
                if (target.teamId().equals(observer.teamId()) || !"active".equals(target.state())
                        || !target.isSubmarine() || target.y() > PERISCOPE_BAND_TOP
                        || target.y() + SubmarinePeriscopeGeometry.topAboveRoot(target.y()) <= 0) continue;
                double distance = observer.position().distanceTo(target.position());
                // Use the existing forward-detection sensitivity uniformly around the ship.
                if (distance > GameSession.botPeriscopeRamDetectionRange(target) * .8
                        || LandGeometry.lineIntersectsBlockedLand(observer.position(), target.position(), world)) continue;
                Contact contact = contacts.computeIfAbsent(target.id(), key -> new Contact(now));
                contact.lastSeen = now;
                if (!contact.announced) {
                    newContacts++;
                    if (distance < nearestDistance) { nearest = target; nearestDistance = distance; }
                }
            }
            if (nearest != null && !next.containsKey(observer.id())) {
                double bearing = Math.atan2(nearest.position().x() - observer.position().x(),
                        nearest.position().z() - observer.position().z()) - observer.heading();
                int sector = Math.floorMod((int) Math.round(bearing / (Math.PI / 4)), 8);
                next.put(observer.id(), new Warning(java.util.UUID.randomUUID().toString(), DIRECTIONS[sector], newContacts,
                        now + NOTICE_SECONDS));
                contacts.values().stream().filter(contact -> contact.lastSeen == now)
                        .forEach(contact -> contact.announced = true);
            }
        }
        contactsByObserver.keySet().retainAll(observers);
        published = Map.copyOf(next);
    }
}
