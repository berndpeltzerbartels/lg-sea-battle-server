package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class BotScenarioTest {
    private static final String OPEN = """
            scenario: open-test
            cell: 50
            map:
            .......
            .1...2.
            .......
            objects:
            1: boat light bot [heading: 90, speed: 0]
            2: submarine dark human [heading: 0, speed: 0, depth: periscope]
            """;

    @Test
    void alternateAlgorithmReceivesTicksAndMovesThroughTheRealPhysics() {
        List<Long> ticks = new ArrayList<>();
        BotTestRun run = BotScenario.parse(OPEN).run(() -> (situation, tick, goals) -> {
            ticks.add(tick);
            assertEquals(tick * .1, situation.timeSeconds(), 1e-8);
            situation.steer("light-S1", 8, 0);
        }, 100);
        assertEquals(101, run.frames().size());
        assertEquals(1L, ticks.get(0));
        assertEquals(100L, ticks.get(99));
        assertEquals(0, run.first().ship('1').speed());
        assertTrue(run.last().ship('1').speed() > 15);
        assertTrue(run.displacement('1') > 80);
        assertEquals(-5.58, run.first().ship('2').y(), 1e-8);
        assertEquals("periscope", run.first().ship('2').depthState());
        assertEquals(0, run.displacement('2'), 1e-8);
        assertTrue(run.minimumSeparation('1', '2') < 200);
    }

    @Test
    void standardInterfacePreservesProductionBehavior() {
        GameSetup setup = ScenarioScriptParser.parse(OPEN);
        GameSession original = new GameSession(setup);
        GameSession explicit = new GameSession(setup, 1, false, BotCalculation.standard());
        RadarService radar = new RadarService();
        NavigationService navigation = new NavigationService();
        for (int tick = 0; tick < 100; tick++) {
            original.update(.1, radar, navigation, setup.worldMap());
            explicit.update(.1, radar, navigation, setup.worldMap());
            assertEquals(original.snapshot().ships(), explicit.snapshot().ships());
            assertEquals(original.snapshot().torpedoes(), explicit.snapshot().torpedoes());
        }
    }

    @Test
    void alternativeCannotDriveThroughLandOrSteerAHuman() {
        String island = """
                cell: 25
                map:
                .1.+...
                .......
                ......2
                objects:
                1: boat light bot [heading: 90]
                2: boat dark human
                """;
        BotTestRun run = BotScenario.parse(island).run(() -> (s, tick, goals) -> {
            assertTrue(s.isLand(-0, -25));
            assertTrue(s.isHullBlocked(0, -25, Math.PI/2));
            assertThrows(IllegalArgumentException.class, () -> s.steer("dark-S2", 8, 0));
            s.steer("light-S1", 8, 1000); // same clamping as production
            s.steer("light-S1", 8, 0);
        }, 100);
        assertTrue(run.last().ship('1').x() < -10, "Real land collision stops an unsafe algorithm");
        assertEquals(0, run.displacement('2'));
    }

    @Test
    void benchmarkUsesFreshModelsAndDoesNotCaptureFrames() {
        AtomicInteger instances = new AtomicInteger();
        var timings = BotScenario.parse(OPEN).benchmark(() -> {
            instances.incrementAndGet();
            AtomicInteger calls = new AtomicInteger();
            return (s, tick, goals) -> {
                assertEquals(calls.incrementAndGet(), tick);
                if (tick == 1) assertEquals(-100, s.ships().get(0).x());
                s.steer("light-S1", 5, 0);
            };
        }, 30, 2, 3);
        assertEquals(5, instances.get());
        assertEquals(3, timings.size());
        for (var timing : timings) {
            assertTrue(timing.bots().meanMs() >= 0);
            assertTrue(timing.completeRound().meanMs() >= timing.bots().meanMs());
            assertTrue(timing.completeRound().maxMs() >= timing.completeRound().p99Ms());
        }
    }

    @Test
    void defaultCanBeDecoratedButNotExecutedTwicePerRound() {
        BotScenario.parse(OPEN).run(() -> (s, tick, goals) -> {
            s.runStandard();
            assertThrows(IllegalStateException.class, s::runStandard);
            s.steer("light-S1", 2, 0);
        }, 2);
    }

    @Test
    void outputContainsTraceInitialSituationAndOfflinePlayback(@TempDir Path temp) throws Exception {
        BotTestRun run = BotScenario.parse(OPEN).run(() -> (s, t, goals) -> s.steer("light-S1", 8, 0), 10);
        run.writeReport(temp);
        assertEquals(OPEN, Files.readString(temp.resolve("scenario.txt")));
        assertEquals(23, Files.readAllLines(temp.resolve("trace.csv")).size());
        String html = Files.readString(temp.resolve("index.html"));
        assertTrue(html.contains("requestAnimationFrame"));
        assertTrue(html.contains("open-test"));
        assertFalse(html.contains("__DATA__"));
    }

    @Test
    void rejectsAmbiguousInputs() {
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(OPEN.replace(".1...2.", ".1..12.")));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(OPEN.replace(".1...2.", ".1...2")));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(OPEN.replace("cell: 50", "cell: 0")));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(OPEN.replace("2: submarine", "1: submarine")));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(OPEN.replace("1: boat", "3: boat")));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(OPEN.replace("periscope", "banana")));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(OPEN).run(BotCalculation::standard, 0));
    }
}
