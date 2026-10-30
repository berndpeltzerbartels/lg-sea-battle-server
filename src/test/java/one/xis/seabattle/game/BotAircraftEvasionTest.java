package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class BotAircraftEvasionTest {
    private final NavigationService navigation = new NavigationService();
    private final WorldMap water = new WorldMap(85001, List.of());

    private Ship ship(String id, String team, String controller, String type, double x, double z, double heading, double speed) {
        Ship ship = new Ship(id, team, new Vector2(x, z), heading, controller);
        ship.applyPlayerState(new PlayerStateUpdate(controller, team, x, z, heading, speed, 0, 7, 0, 0, true,
                type, type.equals("scout-plane") ? 50 : 0, 0, null, null, null, null, "surface"), navigation, water);
        return ship;
    }
    private Ship boat(double heading) { return ship("boat", "dark", "bot", "torpedo-boat", 0, 0, heading, 10); }
    private Ship plane(double x, double z, double heading) { return ship("plane", "light", "bot", "scout-plane", x, z, heading, 40); }

    @Test void reactsOnlyInsideQuarterRingAndWhenFlyingDirectlyAtTheBoat() {
        Ship boat = boat(0);
        for (Ship plane : List.of(plane(0, -BotAircraftEvasion.RANGE - 1, 0), plane(0, -90, Math.PI),
                plane(0, -90, Math.PI / 2), plane(0, -90, Math.toRadians(21)))) {
            BotAircraftEvasion.observe(boat, plane, 0);
            assertNull(boat.botNavigation.aircraftEvasion);
        }
        BotAircraftEvasion.observe(boat, plane(0, -BotAircraftEvasion.RANGE, 0), 0);
        assertNotNull(boat.botNavigation.aircraftEvasion);
        assertEquals(-Math.PI / 2, boat.botNavigation.aircraftEvasion.course(boat, 0, navigation, water).heading(), 1e-9);
    }

    @Test void commitsToShortestTurnWithoutZigzagAndExpiresThreeSecondsAfterLastApproach() {
        Ship boat = boat(Math.PI / 4);
        BotAircraftEvasion.observe(boat, plane(0, -90, 0), 0);
        var evasion = boat.botNavigation.aircraftEvasion;
        var course = evasion.course(boat, 0, navigation, water);
        assertEquals(Math.PI / 2, course.heading(), 1e-9);
        assertEquals(8, course.engineOrder());
        BotAircraftEvasion.observe(boat, plane(0, -70, Math.toRadians(-10)), 1);
        assertSame(evasion, boat.botNavigation.aircraftEvasion);
        assertSame(course, evasion.course(boat, 1, navigation, water), "Reuse the safe course, do not replan every tick");
        BotAircraftEvasion.observe(boat, plane(0, 50, 0), 2); // Passed the boat: no refresh.
        assertNotNull(evasion.course(boat, 3.99, navigation, water));
        assertNull(evasion.course(boat, 4, navigation, water));
        boat.respawn(new Vector2(0, 0), 0, 5);
        assertNull(boat.botNavigation.aircraftEvasion);
    }

    @Test void ignoresFriendlyPlanesHumanBoatsAndSubmarinesAndKeepsSafetyLayersAboveAircraft() {
        Ship boat = boat(0);
        BotAircraftEvasion.observe(boat, ship("friend", "dark", "bot", "scout-plane", 0, -70, 0, 40), 0);
        assertNull(boat.botNavigation.aircraftEvasion);
        for (Ship ignored : List.of(ship("human", "dark", "player", "torpedo-boat", 0, 0, 0, 10),
                ship("sub", "dark", "bot", "submarine", 0, 0, 0, 10))) {
            BotAircraftEvasion.observe(ignored, plane(0, -70, 0), 0);
            assertNull(ignored.botNavigation.aircraftEvasion);
        }
        var plan = boat.botNavigation;
        var aircraft = new BotNavigation.Course(Math.PI / 2, 8);
        plan.route = new BotNavigation.Course(0, 5);
        assertSame(aircraft, plan.selected(null, aircraft));
        plan.encounter = new BotNavigation.Course(.2, 4);
        assertSame(plan.encounter, plan.selected(null, aircraft));
        plan.near = new BotNavigation.Course(.3, 2);
        assertSame(plan.near, plan.selected(null, aircraft));
        var urgent = new BotNavigation.Course(.4, 0);
        assertSame(urgent, plan.selected(urgent, aircraft));
    }

    @SuppressWarnings("unchecked")
    @Test void existingAircraftAttackDecisionActuallyMakesTheVictimSteerAcrossTheFlightPath() throws Exception {
        var session = new GameSession(new GameSetup("aircraft-evasion", water, List.of(
                new FleetSetup("light", List.of(new ShipSetup("plane", "light", new Vector2(0, -100), 0,
                        "bot", 7, 0, 9999, "scout-plane", 50))),
                new FleetSetup("dark", List.of(new ShipSetup("boat", "dark", new Vector2(0, 0), 0,
                        "bot", 5, 0, 9999, "torpedo-boat", 0)))), List.of(new Vector2(1000, 1000))));
        Method allShips = GameSession.class.getDeclaredMethod("allShips"); allShips.setAccessible(true);
        List<Ship> ships = (List<Ship>) allShips.invoke(session);
        Ship plane = ships.stream().filter(s -> s.id().equals("plane")).findFirst().orElseThrow();
        plane.applyPlayerState(new PlayerStateUpdate("bot", "light", 0, -100, 0, 40, 0, 7, 0, 0, true,
                "scout-plane", 50, 0, null, null, null, null, "surface"), navigation, water);
        session.update(0, new RadarService(), navigation, water);
        Ship boat = ships.stream().filter(s -> s.id().equals("boat")).findFirst().orElseThrow();
        assertNotNull(boat.botNavigation.aircraftEvasion);
        assertTrue(boat.snapshot().rudderDegrees() < 0, "The existing helm must start the short left turn immediately");
        assertEquals(8, boat.snapshot().engineOrder());
        assertNotNull(boat.botNavigation.route, "Normal navigation remains stored beneath the temporary manoeuvre");
        for (int tick = 0; tick < 10; tick++) session.update(.1, new RadarService(), navigation, water);
        assertTrue(boat.heading() < -.01, "The manoeuvre must actually rotate the moving boat, not just store a plan");
        assertTrue(boat.speed() > 0);
    }
}
