package one.xis.seabattle.game;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotNavigationTest {
    private final NavigationService navigation = new NavigationService();
    private final WorldMap water = new WorldMap(1, List.of());
    private Ship boat(String id, double x, double z, double heading, double speed) {
        Ship ship = new Ship(id, "red", new Vector2(x, z), heading, "bot");
        ship.applyPlayerState(new PlayerStateUpdate("bot", "red", x, z, heading, speed, 0, 7, 0, 0,
                true, "torpedo-boat", 0, 0, null, null, null, null, "surface"), navigation, water);
        return ship;
    }

    @Test void headingFeedbackIsProportionalBoundedSymmetricAndHandlesNorthCrossing() {
        assertEquals(0, BotHelm.rudder(0, 0, 12, 0));
        assertTrue(BotHelm.rudder(0, 0, 12, .08) < BotHelm.rudder(0, 0, 12, .3));
        assertEquals(35, BotHelm.rudder(0, 0, 12, 2));
        assertEquals(-35, BotHelm.rudder(0, 0, 12, -2));
        assertEquals(-BotHelm.rudder(0, 0, 12, .2), BotHelm.rudder(0, 0, -2, .2));
        assertTrue(BotHelm.rudder(Math.PI - .02, 0, 12, -Math.PI + .02) > 0);
        assertTrue(Math.abs(BotHelm.rudder(Math.PI - .02, 0, 12, -Math.PI + .02)) < 5);
        assertTrue(BotHelm.rudder(0, .15, 12, .02) < 0, "Counter-steer while the hull is still turning");
    }

    @Test void closedLoopConvergesWithoutRepeatedZigzagsAndNeedsNoReplanning() {
        for (double desired : new double[]{.15, 1.4, -.8, Math.PI - .02}) {
            Ship ship = boat("helm", 0, 0, 0, 12);
            int crossings = 0;
            double previous = MathSupport.normalizeAngle(desired - ship.heading());
            for (int tick = 0; tick < 500; tick++) {
                int rudder = BotHelm.rudder(ship.heading(), ship.turnVelocity(), ship.speed(), desired);
                ship.applyCommand(7, rudder);
                ship.update(.1, navigation, water);
                double error = MathSupport.normalizeAngle(desired - ship.heading());
                if (Math.abs(error) > .01 && error * previous < 0) crossings++;
                previous = error;
            }
            assertTrue(Math.abs(previous) < .012, "Settles on course " + desired + ": " + previous);
            assertTrue(crossings <= 1, "No persistent left/right oscillation");
        }
    }

    @Test void targetRouteAndEncounterSchedulesAreStaggeredWithSpecifiedPeriods() {
        BotNavigation plan = new BotNavigation("schedule");
        int targets = 0, routes = 0, encounters = 0;
        for (int tick = 0; tick < 201; tick++) {
            boolean t = plan.targetDue(tick), r = plan.routeDue(tick), e = plan.encounterDue(tick);
            if (tick == 0) continue; // Initial plans are available immediately.
            assertFalse(r && e);
            assertFalse(t && (r || e));
            if (t) targets++;
            if (r) routes++;
            if (e) encounters++;
        }
        assertEquals(10, targets); assertEquals(20, routes); assertEquals(20, encounters);
        assertTrue(BotNavigation.ROUTE_RANGE > BotNavigation.ENCOUNTER_RANGE);
        assertTrue(BotNavigation.ROUTE_RANGE <= BotNavigation.RADAR_RING);
        assertTrue(BotNavigation.ENCOUNTER_RANGE > BotNavigation.NEAR_RANGE);
    }

    @Test void changingLowerPlansCannotOverwriteHigherPlansAndZeroHeadingIsValid() {
        BotNavigation p = new BotNavigation("priority");
        p.route = new BotNavigation.Course(1, 7);
        p.encounter = new BotNavigation.Course(.5, 5);
        p.near = new BotNavigation.Course(0, 2);
        var stop = p.near;
        p.route = new BotNavigation.Course(-1, 8);
        assertSame(stop, p.selected(null));
        var urgent = new BotNavigation.Course(2, 3);
        assertSame(urgent, p.selected(urgent));
        p.near = null; assertSame(p.encounter, p.selected(null));
        p.encounter = null; assertSame(p.route, p.selected(null));
    }

    @Test void crossingTrafficIsDetectedBeforeItEntersTheBowCorridor() {
        Ship own = boat("own", 0, 0, 0, 12);
        Ship crossing = boat("crossing", -100, 100, Math.PI / 2, 12);
        BotNavigation plan = own.botNavigation;
        plan.route = new BotNavigation.Course(0, 7);
        plan.planEncounter(own, List.of(own, crossing), navigation, water);
        assertNotNull(plan.encounter);
        assertTrue(Math.abs(plan.encounter.heading()) > .01);
        assertEquals(new BotNavigation.Course(0, 7), plan.route);
        crossing.respawn(new Vector2(500, 500), 0, 0);
        plan.planEncounter(own, List.of(own, crossing), navigation, water);
        assertNotNull(plan.encounter, "Keep a pass stable until two clear observations");
        plan.planEncounter(own, List.of(own, crossing), navigation, water);
        assertNull(plan.encounter);
    }

    @Test void headOnFriendlyBoatsPassAndKeepMovingInsteadOfAlternatingForwardReverse() {
        Ship a = boat("a", 0, -110, 0, 12), b = boat("b", 0, 110, Math.PI, 12);
        List<Ship> ships = List.of(a, b);
        a.botNavigation.route = new BotNavigation.Course(0, 7);
        b.botNavigation.route = new BotNavigation.Course(Math.PI, 7);
        double closest = Double.POSITIVE_INFINITY;
        for (int tick = 0; tick < 400; tick++) {
            for (Ship ship : ships) {
                var plan = ship.botNavigation;
                if (tick % 10 == 0) plan.planEncounter(ship, ships, navigation, water);
                plan.checkNear(ship, ships, navigation, water);
                var course = plan.selected(null);
                assertTrue(course.engineOrder() >= 2, "No routine reverse shunting");
                ship.applyCommand(course.engineOrder(), BotHelm.rudder(ship.heading(), ship.turnVelocity(), ship.speed(), course.heading()));
            }
            for (Ship ship : ships) ship.update(.1, navigation, water);
            closest = Math.min(closest, a.position().distanceTo(b.position()));
        }
        assertTrue(closest > 20, "Safe hull clearance: " + closest);
        assertTrue(a.position().z() > 110 && b.position().z() < -110, "Both boats continue beyond the encounter");
    }

    @Test void islandInsideTheRouteIsAvoidedEvenWhenItsFarEndpointIsClear() {
        Ship ship = boat("route", 0, 0, 0, 12);
        Landmass island = new Landmass("island", "middle", 0, 150, 25, 20, 25, 20, 30, 24,
                null, 1, null, null, null, List.of(), List.of(), List.of());
        WorldMap map = new WorldMap(2, List.of(island));
        assertFalse(navigation.isShipBlocked(new Vector2(0, BotNavigation.ROUTE_RANGE), 0, map));
        var course = BotNavigation.freeRoute(ship, new BotNavigation.Course(0, 7), BotNavigation.ROUTE_RANGE, navigation, map);
        assertTrue(Math.abs(course.heading()) > .1, "An island between the boat and clear endpoint blocks the direct route");
        for (double d = 8; d <= BotNavigation.ROUTE_RANGE; d += 8) {
            assertFalse(navigation.isShipBlocked(Vector2.fromHeading(course.heading()).scale(d), course.heading(), map));
        }
    }

    @Test void controllerTransferAndRespawnDiscardOldTargetAndAvoidancePlans() {
        Ship ship = boat("reset", 0, 0, 0, 12);
        var old = ship.botNavigation;
        old.target = boat("target", 0, 100, 0, 0);
        old.near = new BotNavigation.Course(1, 3);
        ship.controlledBy("player");
        assertNotSame(old, ship.botNavigation);
        assertNull(ship.botNavigation.near);
        ship.controlledBy("bot");
        old = ship.botNavigation;
        ship.respawn(new Vector2(100, 100), 0, 4);
        assertNotSame(old, ship.botNavigation);
        assertNull(ship.botNavigation.target);
    }

    @Test void equalSpeedAtCloseRangeDoesNotHideAnOverlappingHull() {
        Ship rear = boat("rear", 0, 0, 0, 12), front = boat("front", 0, 17, 0, 12);
        var ships = List.of(rear, front);
        for (Ship ship : ships) {
            ship.botNavigation.route = new BotNavigation.Course(0, 7);
            ship.botNavigation.checkNear(ship, ships, navigation, water);
        }
        assertNotNull(rear.botNavigation.near);
        assertNull(front.botNavigation.near);
        assertTrue(rear.botNavigation.selected(null).engineOrder() < front.botNavigation.selected(null).engineOrder());
    }

    @Test void clearWaterDoesNotRunTerrainChecksAndRecedingTrafficDoesNotTriggerAvoidance() {
        Ship a = boat("a", 0, 0, 0, 12), b = boat("b", 0, 100, 0, 17.5);
        var plan = a.botNavigation;
        plan.route = BotNavigation.freeRoute(a, new BotNavigation.Course(0, 7), BotNavigation.ROUTE_RANGE,
                navigation, water);
        plan.planEncounter(a, List.of(a, b), navigation, water);
        plan.checkNear(a, List.of(a, b), navigation, water);
        assertNull(plan.near); assertNull(plan.encounter);
    }
}
