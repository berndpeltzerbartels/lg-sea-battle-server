package one.xis.seabattle.game;

/** The attacking bot plane already knows its victim; no separate aircraft/ship scan. */
final class BotAircraftEvasion {
    static final double RANGE = BotNavigation.RADAR_RING / 4;
    static final double HOLD_SECONDS = 3;
    private static final double AIM_COS = Math.cos(Math.toRadians(20));
    private final double heading;
    private double until;
    private BotNavigation.Course safeCourse;

    private BotAircraftEvasion(double heading, double until) {
        this.heading = heading;
        this.until = until;
    }

    static void observe(Ship ship, Ship plane, double now) {
        if (!ship.isBotControlled() || ship.isSubmarine() || ship.isScoutPlane()
                || !"active".equals(ship.state()) || !plane.isScoutPlane()
                || !"active".equals(plane.state()) || plane.teamId().equals(ship.teamId()) || plane.speed() <= 0) return;
        double dx = ship.position().x() - plane.position().x();
        double dz = ship.position().z() - plane.position().z();
        double distanceSquared = dx * dx + dz * dz;
        if (distanceSquared > RANGE * RANGE || distanceSquared < 1e-6) return;
        double forward = dx * Math.sin(plane.heading()) + dz * Math.cos(plane.heading());
        if (forward <= 0 || forward * forward < distanceSquared * AIM_COS * AIM_COS) return;
        BotAircraftEvasion current = ship.botNavigation.aircraftEvasion;
        if (current == null || now >= current.until) {
            double port = MathSupport.normalizeAngle(plane.heading() - Math.PI / 2);
            double starboard = MathSupport.normalizeAngle(plane.heading() + Math.PI / 2);
            double heading = Math.abs(MathSupport.normalizeAngle(port - ship.heading()))
                    <= Math.abs(MathSupport.normalizeAngle(starboard - ship.heading())) ? port : starboard;
            ship.botNavigation.aircraftEvasion = new BotAircraftEvasion(heading, now + HOLD_SECONDS);
        } else {
            // Commit to one side through this close pass; small aim changes must not cause zigzags.
            current.until = now + HOLD_SECONDS;
        }
    }

    BotNavigation.Course course(Ship ship, double now, NavigationService navigation, WorldMap world) {
        if (now >= until) return null;
        // Only once at activation; normal near/encounter/emergency layers retain priority afterwards.
        if (safeCourse == null) safeCourse = BotNavigation.freeRoute(ship,
                new BotNavigation.Course(heading, 8), BotNavigation.NEAR_RANGE, navigation, world);
        return safeCourse;
    }
}
