package one.xis.seabattle.game;

import java.util.List;
import java.util.ArrayList;

/** Independent planning layers. Only the helm selects a winner; lower layers cannot clear higher ones. */
final class BotNavigation {
    static final double SHIP_LENGTH = (3.68 + 4.2) * SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    // First ring of the normal combat radar (client: 945 * 2.5 * .62, rings at .33/.66/1).
    static final double RADAR_RING = RadarService.RADAR_RANGE * 2.5 * .62 * .33;
    static final double ROUTE_RANGE = Math.min(16 * SHIP_LENGTH, RADAR_RING);
    static final double ENCOUNTER_RANGE = 10 * SHIP_LENGTH;
    static final double NEAR_RANGE = 3 * SHIP_LENGTH;
    record Course(double heading, int engineOrder) {}

    private final int phase;
    private long nextTarget = -1, nextRoute = -1, nextEncounter = -1;
    Ship target;
    long targetLife;
    Course route, encounter, near;
    private int encounterClearChecks;
    double previousSubmarineEcho;

    BotNavigation(String shipId) { phase = Math.floorMod(shipId.hashCode(), 20); }

    boolean targetDue(long tick) {
        if (tick < nextTarget) return false;
        nextTarget = nextSlot(tick, 20, 7);
        return true;
    }
    boolean routeDue(long tick) {
        if (tick < nextRoute) return false;
        nextRoute = nextSlot(tick, 10, 0);
        return true;
    }
    boolean encounterDue(long tick) {
        if (tick < nextEncounter) return false;
        nextEncounter = nextSlot(tick, 10, 5);
        return true;
    }
    private long nextSlot(long tick, int period, int slot) {
        return tick + 1 + Math.floorMod(slot - phase - (tick + 1), period);
    }
    Course selected(Course urgent) {
        return urgent != null ? urgent : near != null ? near : encounter != null ? encounter : route;
    }

    static Course freeRoute(Ship ship, Course desired, double distance,
                            NavigationService navigation, WorldMap world) {
        world = nearbyLand(ship, world, distance);
        if (pathClear(ship, desired.heading, distance, navigation, world)) return desired;
        // Smallest deviation first. Equal choices use the current heading, avoiding arbitrary flips.
        int firstSide = MathSupport.normalizeAngle(desired.heading - ship.heading()) >= 0 ? 1 : -1;
        for (int step = 1; step <= 18; step++) {
            for (int side : new int[]{firstSide, -firstSide}) {
                double heading = MathSupport.normalizeAngle(desired.heading + side * step * Math.PI / 18);
                if (pathClear(ship, heading, distance, navigation, world)) {
                    return new Course(heading, desired.engineOrder);
                }
            }
        }
        return new Course(ship.heading(), 3); // Only the near-land escape may order reverse.
    }

    private static WorldMap nearbyLand(Ship ship, WorldMap world, double distance) {
        // Once per planning operation: exclude terrain that cannot intersect any candidate corridor.
        // 1.42 encloses rough coastlines; the extra margin includes every hull probe.
        if (world.landmasses().isEmpty()) return world;
        double range = distance + 16;
        var nearby = new ArrayList<Landmass>();
        for (Landmass land : world.landmasses()) {
            if (Math.abs(land.x() - ship.position().x()) <= range + Math.abs(land.rx()) * 1.42
                    && Math.abs(land.z() - ship.position().z()) <= range + Math.abs(land.rz()) * 1.42) nearby.add(land);
        }
        return nearby.size() == world.landmasses().size() ? world : new WorldMap(world.version(), nearby);
    }

    private static boolean pathClear(Ship ship, double heading, double distance,
                                     NavigationService navigation, WorldMap world) {
        if (world.landmasses().isEmpty()) return true;
        Vector2 forward = Vector2.fromHeading(heading);
        Vector2 end = ship.position().add(forward.scale(distance));
        var corridorLand = new ArrayList<Landmass>();
        double minX = Math.min(ship.position().x(), end.x()) - 16, maxX = Math.max(ship.position().x(), end.x()) + 16;
        double minZ = Math.min(ship.position().z(), end.z()) - 16, maxZ = Math.max(ship.position().z(), end.z()) + 16;
        for (Landmass land : world.landmasses()) {
            if (land.x() + Math.abs(land.rx()) * 1.42 >= minX && land.x() - Math.abs(land.rx()) * 1.42 <= maxX
                    && land.z() + Math.abs(land.rz()) * 1.42 >= minZ && land.z() - Math.abs(land.rz()) * 1.42 <= maxZ) corridorLand.add(land);
        }
        if (corridorLand.isEmpty()) return true;
        if (corridorLand.size() != world.landmasses().size()) world = new WorldMap(world.version(), corridorLand);
        // The far scan is deliberately coarse: small islands between samples are handled on approach.
        // Near safety checks keep their existing fine spacing.
        double stepDistance = distance > NEAR_RANGE ? 2 * SHIP_LENGTH : 8;
        int steps = Math.max(1, (int) Math.ceil(distance / stepDistance));
        for (int i = 1; i <= steps; i++) {
            if (navigation.isShipBlocked(ship.position().add(forward.scale(distance * i / steps)), heading, world)) return false;
        }
        return true;
    }

    void planEncounter(Ship ship, List<Ship> ships, NavigationService navigation, WorldMap world) {
        if (route == null) { encounter = null; return; }
        if (trafficClear(ship, route, ships, ENCOUNTER_RANGE, 14, 30)) {
            // Do not immediately revert at the boundary of a passing manoeuvre.
            if (++encounterClearChecks >= 2) encounter = null;
            return;
        }
        encounterClearChecks = 0;
        world = nearbyLand(ship, world, ENCOUNTER_RANGE);
        if (encounter != null && trafficClear(ship, encounter, ships, ENCOUNTER_RANGE, 14, 30)
                && pathClear(ship, encounter.heading, ENCOUNTER_RANGE, navigation, world)) return;
        encounter = avoidTraffic(ship, encounter == null ? route : encounter, ships,
                ENCOUNTER_RANGE, 14, 30, navigation, world);
    }

    void checkNear(Ship ship, List<Ship> ships, NavigationService navigation, WorldMap world) {
        Course intended = encounter != null ? encounter : route;
        if (intended == null || trafficClear(ship, intended, ships, NEAR_RANGE, 3, 22)) {
            near = null;
            return;
        }
        world = nearbyLand(ship, world, Math.max(16, Math.abs(ship.speed()) * 2));
        if (near != null && trafficClear(ship, near, ships, NEAR_RANGE, 3, 22)
                && pathClear(ship, near.heading, Math.max(16, Math.abs(ship.speed()) * 2), navigation, world)) return;
        near = avoidTraffic(ship, intended, ships, NEAR_RANGE, 3, 22, navigation, world);
    }

    private Course avoidTraffic(Ship ship, Course intended, List<Ship> ships, double range,
                                double horizon, double clearance, NavigationService navigation, WorldMap world) {
        // A single passing side prevents two friends from changing their minds together.
        // If port is blocked, stop rather than creep ahead or switch to starboard.
        double distance = range == NEAR_RANGE ? Math.max(16, Math.abs(ship.speed()) * 2) : range;
        for (int step = 1; step <= 9; step++) {
            Course proposal = new Course(MathSupport.normalizeAngle(intended.heading - step * Math.PI / 18),
                    range == NEAR_RANGE ? Math.min(5, intended.engineOrder) : intended.engineOrder);
            if (trafficClear(ship, proposal, ships, range, horizon, clearance)
                    && pathClear(ship, proposal.heading, distance, navigation, world)) return proposal;
        }
        return new Course(ship.heading(), 2);
    }

    private static boolean trafficClear(Ship ship, Course course, List<Ship> ships,
                                        double range, double horizon, double clearance) {
        double ownSpeed = Math.max(Math.abs(ship.speed()), Math.max(0, EngineOrders.speedFor(course.engineOrder)));
        double vx = Math.sin(course.heading) * ownSpeed, vz = Math.cos(course.heading) * ownSpeed;
        for (Ship other : ships) {
            if (other == ship || !"active".equals(other.state()) || other.isScoutPlane()
                    || !other.teamId().equals(ship.teamId()) || !sameDepth(ship, other)) continue;
            double dx = other.position().x() - ship.position().x(), dz = other.position().z() - ship.position().z();
            double d2 = dx * dx + dz * dz;
            if (d2 > range * range) continue;
            if (d2 < clearance * clearance) {
                // At an existing close contact, equal velocities do not mean the hulls are clear.
                // The trailing boat yields; crossing boats use stable right of way. Never both shunt.
                if (!other.isBotControlled()) return false;
                double alignment = Math.cos(ship.heading() - other.heading());
                double along = dx * (Math.sin(ship.heading()) + Math.sin(other.heading()))
                        + dz * (Math.cos(ship.heading()) + Math.cos(other.heading()));
                boolean yield = alignment > .5 && Math.abs(along) > 1
                        ? along > 0 : ship.id().compareTo(other.id()) > 0;
                if (yield) return false;
                continue;
            }
            double rvx = Math.sin(other.heading()) * other.speed() - vx;
            double rvz = Math.cos(other.heading()) * other.speed() - vz;
            double relative2 = rvx * rvx + rvz * rvz;
            double dot = dx * rvx + dz * rvz;
            // Ships already separating need not manoeuvre back into each other's way.
            if (dot >= 0) continue;
            double t = MathSupport.clamp(-dot / Math.max(.001, relative2), 0, horizon);
            double x = dx + rvx * t, z = dz + rvz * t;
            if (x * x + z * z < clearance * clearance) return false;
        }
        return true;
    }
    private static boolean sameDepth(Ship a, Ship b) {
        if (Math.abs(a.y() - b.y()) < 5) return true;
        // A friendly mast at periscope depth is still a surface collision obstacle.
        return a.y() >= -1 && b.isSubmarine() && b.y() + SubmarinePeriscopeGeometry.topAboveRoot(b.y()) >= -.93
                || b.y() >= -1 && a.isSubmarine() && a.y() + SubmarinePeriscopeGeometry.topAboveRoot(a.y()) >= -.93;
    }
}
