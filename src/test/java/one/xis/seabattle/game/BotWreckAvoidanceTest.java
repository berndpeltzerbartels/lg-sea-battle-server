package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotWreckAvoidanceTest {
    private final NavigationService navigation = new NavigationService();
    private final WorldMap water = new WorldMap(1, List.of());

    private Wreck wreck(String team) {
        return new Wreck("wreck", 0, new TorpedoBoat("victim", team, new Vector2(0, 0), 0, "player"));
    }

    @Test void bothFriendlyAndEnemyWrecksEnterTheExistingEncounterAndNearLayers() {
        for (String team : List.of("light", "dark")) {
            var own = new TorpedoBoat("attacker", "light", new Vector2(0, -100), 0, "bot");
            own.speed = 17.5;
            var plan = own.botNavigation;
            plan.route = new BotNavigation.Course(0, 8);
            var obstacles = List.of(BotWreckObstacle.of(wreck(team), 0));
            plan.planEncounter(own, List.of(own), navigation, water, obstacles);
            assertNotNull(plan.encounter);
            assertTrue(plan.encounter.heading() < 0, "Use the established port passing rule");
            own.position = new Vector2(0, -40);
            plan.encounter = null;
            plan.checkNear(own, List.of(own), navigation, water, obstacles);
            assertNotNull(plan.near, "Near check also works between encounter planning slots");
        }
    }

    @Test void aSubmergedWreckReleasesTheSurfaceButWarnsABoatAtItsActualDepth() {
        Wreck wreck = wreck("dark");
        var course = new BotNavigation.Course(0, 8);
        var surface = new TorpedoBoat("surface", "light", new Vector2(0, -40), 0, "bot");
        var sub = new Submarine("sub", "light", new Vector2(0, -40), 0, "bot");
        sub.y = -19.26;
        assertTrue(BotWreckObstacle.of(wreck, 0).courseClear(sub, course, 100, 3));
        assertFalse(BotWreckObstacle.of(wreck, 9).courseClear(sub, course, 100, 3));
        assertTrue(BotWreckObstacle.of(wreck, 9).courseClear(surface, course, 100, 3));
    }

    @Test void nearbyBoatsCanLeaveTheWreckAndUnrelatedCoursesStayFree() {
        var obstacle = BotWreckObstacle.of(wreck("dark"), 0);
        var own = new TorpedoBoat("escape", "light", new Vector2(0, -20), Math.PI, "bot");
        assertTrue(obstacle.courseClear(own, new BotNavigation.Course(Math.PI, 8), 100, 3));
        own.position = new Vector2(80, -40);
        assertTrue(obstacle.courseClear(own, new BotNavigation.Course(0, 8), 200, 14));
    }

    @Test void productionNavigationSurvivesTheShipItJustDestroyedAndContinuesItsJourney() throws Exception {
        for (boolean parallel : List.of(false, true)) for (double initialDistance : new double[]{60, 100}) {
            BotTargetSelection straightDestination = new BotTargetSelection() {
                public Vehicle select(BotTargetSituation situation, long tick) { return null; }
                public BotGoal goal(BotTargetSituation situation, Vehicle contact, boolean advance) {
                    return new BotGoal(BotGoal.Kind.SEARCH, null, -1, 0, 400);
                }
            };
            var setup = new GameSetup("wreck-navigation", water, List.of(
                    new FleetSetup("light", List.of(new VehicleSetup("attacker", "light", new Vector2(0, -initialDistance),
                            0, "bot", 8, 0, 9999, "torpedo-boat", 0))),
                    new FleetSetup("dark", List.of(new VehicleSetup("victim", "dark", new Vector2(0, 0),
                            0, "player", 2, 0, 9999, "torpedo-boat", 0)))), List.of());
            var session = new GameSession(setup, 1, parallel, new StandardBotCalculation(), straightDestination);
            Vehicle attacker = session.calculationShips().stream().filter(v -> v.id().equals("attacker")).findFirst().orElseThrow();
            Vehicle victim = session.calculationShips().stream().filter(v -> v.id().equals("victim")).findFirst().orElseThrow();
            attacker.speed = 17.5;
            long life = attacker.lifeId();
            Method sink = GameSession.class.getDeclaredMethod("sinkShip", Vehicle.class);
            sink.setAccessible(true);
            sink.invoke(session, victim);
            double lateralDeviation = 0;
            for (int tick = 0; tick < 220; tick++) {
                session.update(.1, new RadarService(), navigation, water);
                assertEquals(life, attacker.lifeId(), "No collision and respawn hidden by bot replacement");
                assertEquals("active", attacker.state(), "Survive the still-collidable sinking hull");
                lateralDeviation = Math.max(lateralDeviation, Math.abs(attacker.position().x()));
            }
            assertTrue(lateralDeviation > 10, "Actually steer around the wreck");
            assertTrue(attacker.position().z() > 100, "Continue instead of permanently stopping");
            assertTrue(session.botWreckObstacles().isEmpty(), "Finished sinking no longer costs navigation checks");
        }
    }
}
