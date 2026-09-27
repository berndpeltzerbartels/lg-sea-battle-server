package one.xis.seabattle.game;

import java.util.List;

/** Tactical state belongs to one bot, independently of player depth controls. */
final class SubmarineBot {
    // Same range as the player's deeply submerged radar.
    static final double UNDERWATER_RANGE = 945 * 2.5 * 0.62 * 0.58;
    private static final double DIVE_RANGE = 500;
    private static final double DEEP_RANGE = 130;
    private static final double SAFE_RANGE = 220;
    private static final double SHOT_ASCENT_RANGE = 180;
    private static final double SURFACE_RANGE = 650;
    private Vector2 progressPosition;
    private double progressTime;
    private double surfaceUntil;
    private String depth = "surface";

    Vector2 update(Ship ship, List<Ship> contacts, double now) {
        return update(ship, contacts, now, false);
    }

    Vector2 update(Ship ship, List<Ship> contacts, double now, boolean hasShotOpportunity) {
        Ship nearest = contacts.stream()
                .filter(other -> !other.teamId().equals(ship.teamId()))
                .filter(other -> "active".equals(other.state()) && !other.isScoutPlane())
                .filter(other -> !ship.isFullySubmerged()
                        || ship.position().distanceTo(other.position()) <= UNDERWATER_RANGE)
                .min(java.util.Comparator.comparingDouble(
                other -> ship.position().distanceTo(other.position()))).orElse(null);
        double distance = nearest == null ? Double.POSITIVE_INFINITY
                : ship.position().distanceTo(nearest.position());

        if (!"surface".equals(depth)) {
            if (progressPosition == null || ship.position().distanceTo(progressPosition) >= 8) {
                progressPosition = ship.position();
                progressTime = now;
            }
            if (now - progressTime >= 15) {
                surfaceUntil = now + 30;
                surface();
            }
        }
        if (now < surfaceUntil) {
            ship.botDepth("surface");
            return null;
        }

        if ("submerged".equals(depth)) {
            if (distance < SAFE_RANGE && !(hasShotOpportunity && distance >= SHOT_ASCENT_RANGE)) {
                ship.botDepth(depth);
                return nearest.position();
            }
            if (nearest == null) surface();
            else depth = "periscope";
            ship.botDepth(depth);
            return null; // Reacquire contacts on the next simulation tick.
        }
        if (distance <= DEEP_RANGE) {
            startDive(ship, now);
            depth = "submerged";
        } else if (distance <= DIVE_RANGE) {
            startDive(ship, now);
            depth = "periscope";
        } else if (distance > SURFACE_RANGE) {
            surface();
        }
        ship.botDepth(depth);
        return "submerged".equals(depth) ? nearest.position() : null;
    }

    private void startDive(Ship ship, double now) {
        if ("surface".equals(depth)) {
            progressPosition = ship.position();
            progressTime = now;
        }
    }

    private void surface() {
        depth = "surface";
        progressPosition = null;
    }
}
