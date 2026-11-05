package one.xis.seabattle.game;

import one.xis.seabattle.webapp.gameconfig.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static one.xis.seabattle.game.GameRules.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveBotConfigurationTest {
    private final WorldMap water = new WorldMap(1, List.of());
    private final RadarService radar = new RadarService();
    private final NavigationService navigation = new NavigationService();

    private GameRuntimeConfig configuration(Object... values) {
        var gson = new Gson();
        var json = gson.toJsonTree(TorpedoBoatBotConfig.defaults()).getAsJsonObject();
        for (int i = 0; i < values.length; i += 2)
            json.addProperty((String) values[i], (Number) values[i + 1]);
        return new GameRuntimeConfig(gson.fromJson(json, TorpedoBoatBotConfig.class));
    }

    private VehicleSetup ship(String id, String team, String controller, double x, double z, String type) {
        return new VehicleSetup(id, team, new Vector2(x, z), 0, controller, ENGINE_HALF, 0, 9999, type, 0);
    }

    private GameSetup duel(double x, double z, String attackerType) {
        return new GameSetup("live-config", water, List.of(
                new FleetSetup("light", List.of(ship("attacker", "light", "bot", 0, 0, attackerType))),
                new FleetSetup("dark", List.of(ship("target", "dark", "captain", x, z, "torpedo-boat")))), List.of());
    }

    private Vehicle vehicle(GameSession session, String id) {
        return session.calculationShips().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void configurationIsReadOnceAndCannotChangeBetweenSelectionAndMovement() {
        GameRuntimeConfig a = GameRuntimeConfig.defaults(), b = configuration("aimResponse", .8);
        var current = new AtomicReference<>(a);
        var reads = new AtomicInteger();
        var rounds = new AtomicInteger();
        BotTargetSelection selection = (situation, tick) -> {
            assertSame(a, situation.config());
            current.set(b); // A scheduled refresh may finish while this round is being calculated.
            return null;
        };
        BotCalculation movement = (situation, tick, goals) -> {
            assertSame(rounds.getAndIncrement() == 0 ? a : b, situation.config());
        };
        var session = new GameSession(duel(0, 400, "torpedo-boat"), 1, false, movement, selection,
                () -> { reads.incrementAndGet(); return current.get(); });
        session.update(.1, radar, navigation, water);
        assertEquals(1, reads.get());
        session.update(.1, radar, navigation, water);
        assertEquals(2, reads.get());
        assertEquals(2, rounds.get());
    }

    @Test void updatedRangeAndReloadAffectTheRunningSessionButNotAnAlreadyStartedReload() {
        var current = new AtomicReference<>(configuration("fireMinRange", 50, "fireMaxRange", 200,
                "closeFireRange", 100, "torpedoCooldownMin", 20, "torpedoCooldownVariation", 0));
        var session = new GameSession(duel(0, 450, "torpedo-boat"), current::get);
        Vehicle attacker = vehicle(session, "attacker");
        attacker.nextFireTime(0);
        session.update(0, radar, navigation, water);
        assertTrue(session.torpedoes.isEmpty());
        current.set(configuration("torpedoCooldownMin", 20, "torpedoCooldownVariation", 0));
        for (int tick = 0; tick < 20 && session.torpedoes.isEmpty(); tick++)
            session.update(.1, radar, navigation, water);
        assertFalse(session.torpedoes.isEmpty(), "A live range increase must also reach intercept/fire validation");
        assertEquals(20, attacker.nextFireTime - session.nowSeconds, 1e-8);
        double pendingReload = attacker.nextFireTime;
        current.set(GameRuntimeConfig.defaults());
        session.update(.1, radar, navigation, water);
        assertEquals(pendingReload, attacker.nextFireTime, "New pauses apply to future shots, not retroactively");
    }

    @Test void botCanFireBeyondTheOldLimitWhenConfiguredRangePermitsIt() {
        var session = new GameSession(duel(0, 800, "torpedo-boat"), () -> configuration("fireMaxRange", 1000));
        session.update(0, radar, navigation, water);
        Vehicle attacker = vehicle(session, "attacker"), target = vehicle(session, "target");
        attacker.nextFireTime(0);
        assertTrue(session.fireTorpedoAtTarget(attacker, target, 10, 0),
                "Aim solution and moving-target intersection must use the same configurable range");
    }

    @Test void aimStrengthAndApproachSpeedActuallyReachTheVehicleControls() {
        var normal = new GameSession(duel(100, 400, "torpedo-boat"));
        var gentler = new GameSession(duel(100, 400, "torpedo-boat"), () -> configuration("aimResponse", .8));
        for (var session : List.of(normal, gentler)) {
            vehicle(session, "attacker").speed = EngineOrders.speedFor(ENGINE_HALF);
            session.update(0, radar, navigation, water);
        }
        assertTrue(Math.abs(vehicle(gentler, "attacker").rudderDegrees())
                < Math.abs(vehicle(normal, "attacker").rudderDegrees()));
        var slowApproach = new GameSession(duel(0, 300, "torpedo-boat"), () -> configuration("approachSlowRange", 400));
        // A bot opponent uses the simpler half/two-thirds approach rule.
        vehicle(slowApproach, "target").controlledBy("bot");
        slowApproach.update(0, radar, navigation, water);
        assertEquals(ENGINE_HALF, vehicle(slowApproach, "attacker").engineOrder());
    }

    @Test void configuredPlayerPriorityAndAttackerDistributionReachTargetSelection() {
        var setup = new GameSetup("live-targets", water, List.of(
                new FleetSetup("light", List.of(ship("attacker", "light", "bot", 0, 0, "torpedo-boat"))),
                new FleetSetup("dark", List.of(ship("enemy-bot", "dark", "bot", 0, 400, "torpedo-boat"),
                        ship("human", "dark", "captain", 0, 800, "torpedo-boat")))), List.of());
        var normal = new GameSession(setup);
        var localPriority = new GameSession(setup, () -> configuration("humanTargetPriorityClearance", 1000));
        normal.update(0, radar, navigation, water);
        localPriority.update(0, radar, navigation, water);
        assertEquals("human", vehicle(normal, "attacker").surface().botNavigation.target.id());
        assertEquals("enemy-bot", vehicle(localPriority, "attacker").surface().botNavigation.target.id());
        var crowd = new GameSetup("live-distribution", water, List.of(
                new FleetSetup("light", List.of(ship("a", "light", "bot", 0, 0, "torpedo-boat"),
                        ship("b", "light", "bot", 2, 0, "torpedo-boat"))),
                new FleetSetup("dark", List.of(ship("x", "dark", "bot", 0, 500, "torpedo-boat"),
                        ship("y", "dark", "bot", 100, 550, "torpedo-boat")))), List.of());
        for (double factor : new double[]{1.01, 1.3}) {
            var session = new GameSession(crowd, () -> configuration("targetPreferredAttackers", 1,
                    "targetAlternativeDistanceFactor", factor));
            session.update(0, radar, navigation, water);
            boolean same = vehicle(session, "a").surface().botNavigation.target
                    == vehicle(session, "b").surface().botNavigation.target;
            assertEquals(factor == 1.01, same);
        }
    }

    @Test void theBoatGroupDoesNotChangeSubmarineBotsOrHumanWeapons() {
        var normal = new GameSession(duel(100, 400, "submarine"));
        var changed = new GameSession(duel(100, 400, "submarine"), () -> configuration("aimResponse", 2,
                "fireMaxRange", 200, "fireMinRange", 50, "closeFireRange", 100));
        normal.update(0, radar, navigation, water);
        changed.update(0, radar, navigation, water);
        assertEquals(vehicle(normal, "attacker").snapshot(), vehicle(changed, "attacker").snapshot());
        Vehicle boat = new TorpedoBoat("player", "light", new Vector2(0, 0), 0, "captain");
        assertSame(GameSession.DEFAULT_RUNTIME_CONFIG.torpedoBoatBot(), changed.botCombatConfig(boat));
    }
    @Test void xisServiceDependencyIsPassedToEveryNewSession() throws Exception {
        var constructors = GameStateService.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        var constructor = constructors[0];
        assertEquals(GameConfigService.class, constructor.getParameterTypes()[3]);
        var current = new AtomicReference<>(configuration("aimResponse", .8));
        var service = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()), radar,
                navigation, TestGameConfigService.from(current::get));
        var field = GameStateService.class.getDeclaredField("session");
        field.setAccessible(true);
        for (String setup : List.of("default", "fleet-clash")) {
            service.resetToSetup(setup);
            var session = (GameSession) field.get(service);
            session.update(0, radar, navigation, session.worldMap());
            assertSame(current.get(), session.runtimeConfig());
        }
        service.resetToScenario(new ScenarioGameRequest("bernd", """
                scenario: live-config-service
                map:
                .1.2.
                objects:
                1: ship light bot
                2: ship dark human
                """));
        current.set(configuration("aimResponse", .9));
        var session = (GameSession) field.get(service);
        session.update(0, radar, navigation, session.worldMap());
        assertSame(current.get(), session.runtimeConfig());
    }

}
