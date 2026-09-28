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
    private Vector2 lastAircraftPosition;
    private double aircraftMemoryUntil;

    private boolean backingAway;

    int attackEngineOrder(Ship ship, Ship target, double distance, double targetBearing) {
        // Keep steerage while turning; once aimed, match radial target speed and close the range error.
        if (Math.abs(targetBearing) > Math.PI / 3) {
            backingAway = false;
            return 5;
        }
        double targetRadialSpeed = target.speed() * Math.cos(target.heading() - ship.heading() - targetBearing);
        double desiredSpeed = targetRadialSpeed + (distance - 300) * 0.04;
        // Separate reversal thresholds keep the boat moving instead of hovering at zero speed.
        if (desiredSpeed < -2) backingAway = true;
        else if (desiredSpeed > 2) backingAway = false;
        // Slow ahead (4), not dead slow: reversals must still make progress for the anti-idle guard.
        int bestOrder = 4;
        double bestError = Double.POSITIVE_INFINITY;
        for (int order = backingAway ? 0 : 4; order <= (backingAway ? 1 : 7); order++) {
            double error = Math.abs(EngineOrders.speedFor(order) - desiredSpeed);
            if (error < bestError) {
                bestOrder = order;
                bestError = error;
            }
        }
        return bestOrder;
    }

    Vector2 update(Ship ship, List<Ship> contacts, double now) {
        return update(ship, contacts, now, false);
    }

    Vector2 update(Ship ship, List<Ship> contacts, double now, boolean hasShotOpportunity) {
        if (!ship.isFullySubmerged()) {
            Ship aircraft = contacts.stream()
                    .filter(other -> other.isScoutPlane() && "active".equals(other.state()))
                    .filter(other -> !other.teamId().equals(ship.teamId()))
                    .min(java.util.Comparator.comparingDouble(
                            other -> ship.position().distanceTo(other.position()))).orElse(null);
            lastAircraftPosition = aircraft == null ? null : aircraft.position();
            aircraftMemoryUntil = now + 10;
        }
        Ship nearest = contacts.stream()
                .filter(other -> !other.teamId().equals(ship.teamId()))
                .filter(other -> "active".equals(other.state()) && !other.isScoutPlane())
                .filter(other -> !ship.isFullySubmerged()
                        || ship.position().distanceTo(other.position()) <= UNDERWATER_RANGE)
                .min(java.util.Comparator.comparingDouble(
                other -> ship.position().distanceTo(other.position()))).orElse(null);
        double distance = nearest == null ? Double.POSITIVE_INFINITY
                : ship.position().distanceTo(nearest.position());
        Vector2 threatPosition = nearest == null ? null : nearest.position();
        boolean aircraftThreat = false;
        if (lastAircraftPosition != null && now < aircraftMemoryUntil) {
            double aircraftDistance = ship.position().distanceTo(lastAircraftPosition);
            if (aircraftDistance < distance) {
                distance = aircraftDistance;
                threatPosition = lastAircraftPosition;
                aircraftThreat = true;
            }
        }

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
            if (distance < SAFE_RANGE && !(hasShotOpportunity && !aircraftThreat && distance >= SHOT_ASCENT_RANGE)) {
                ship.botDepth(depth);
                return threatPosition;
            }
            if (threatPosition == null) surface();
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
        return "submerged".equals(depth) ? threatPosition : null;
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
