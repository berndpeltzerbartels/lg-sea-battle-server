package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class BotTargetSelectionTest {
    private static final String SCENE = """
            cell: 100
            map:
            ....................
            .1...2.....3........
            ....................
            objects:
            1: boat light bot [heading: 90]
            2: boat dark bot [heading: 270]
            3: boat dark human
            """;

    @Test
    void replacesOnlyRankingAndRetainsSchedulingAndNavigation() {
        List<Long> ticks = new ArrayList<>();
        BotTestRun run = BotScenario.parse(SCENE).withTargetSelection(() -> (s, tick) -> {
            if (!s.observerId().equals("light-S1")) return s.standardTargetId();
            ticks.add(tick);
            assertTrue(s.candidates().stream().allMatch(c -> !c.teamId().equals(s.observer().teamId())));
            assertEquals(2, s.candidateCount());
            assertEquals("dark-S3", s.standardTargetId(), "Production prefers the distant human to a bot beyond 320");
            assertEquals(s.standardTargetId(), s.standardTargetId(), "Delegation is stable in a selection round");
            if (tick > 1) assertEquals("dark-S2", s.currentTargetId());
            return "dark-S2";
        }).run(BotCalculation::standard, 40);
        assertTrue(ticks.size() >= 2);
        assertTrue(ticks.size() <= 3, "Choice is not repeated every tick");
        assertTrue(ticks.get(1) - ticks.get(0) <= 20);
        assertTrue(run.displacement('1') > 0, "Existing navigation still drives the selected target");
        assertTrue(run.targetDecisions().stream().filter(d -> d.observerId().equals("light-S1"))
                .allMatch(d -> "dark-S2".equals(d.targetId())));
    }

    @Test
    void nullMeansNoTargetAndInvalidTargetsFailLoudly() {
        BotTestRun run = BotScenario.parse(SCENE).withTargetSelection(() -> (s, tick) -> null)
                .run(BotCalculation::standard, 1);
        assertTrue(run.targetDecisions().stream().allMatch(d -> d.targetId() == null));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(SCENE)
                .withTargetSelection(() -> (s, tick) -> s.observerId()).run(BotCalculation::standard, 1));
        assertThrows(IllegalArgumentException.class, () -> BotScenario.parse(SCENE)
                .withTargetSelection(() -> (s, tick) -> "missing-ship").run(BotCalculation::standard, 1));
    }

    @Test
    void selectionDoesNotExposeDeepSubmarinesToSurfaceBoats() {
        String hidden = SCENE.replace("3: boat dark human", "3: submarine dark human [depth: submerged]");
        BotScenario.parse(hidden).withTargetSelection(() -> (s, tick) -> {
            assertTrue(s.candidates().stream().noneMatch(c -> c.id().equals("dark-U3")));
            return s.standardTargetId();
        }).run(BotCalculation::standard, 1);
    }

    @Test
    void aircraftUseTheSameInterfaceWithTheirOwnExistingCandidates() {
        String plane = SCENE.replace("1: boat light bot [heading: 90]", "1: plane light bot [heading: 90, height: 100]");
        AtomicInteger calls = new AtomicInteger();
        BotTestRun run = BotScenario.parse(plane).withTargetSelection(() -> (s, tick) -> {
            if (s.observer().vehicleType().equals("scout-plane")) {
                calls.incrementAndGet();
                assertEquals(2, s.candidateCount());
                assertNull(s.currentTargetId());
                return "dark-S3";
            }
            return s.standardTargetId();
        }).run(BotCalculation::standard, 5);
        assertEquals(5, calls.get());
        assertTrue(run.targetDecisions().stream().filter(d -> d.observerId().equals("light-F1"))
                .allMatch(d -> "dark-S3".equals(d.targetId())));
    }

    @Test
    void selectorInstancesAndMeasurementsAreIndependentOfCalculationInstances() {
        AtomicInteger selectors = new AtomicInteger(), calculations = new AtomicInteger();
        var timings = BotScenario.parse(SCENE).withTargetSelection(() -> {
            selectors.incrementAndGet();
            AtomicInteger ownCalls = new AtomicInteger();
            return (s, tick) -> {
                if (ownCalls.getAndIncrement() == 0) assertEquals(1, tick);
                return s.standardTargetId();
            };
        }).benchmark(() -> {
            calculations.incrementAndGet();
            return BotCalculation.standard();
        }, 30, 1, 2);
        assertEquals(3, selectors.get());
        assertEquals(3, calculations.get());
        for (var timing : timings) {
            assertTrue(timing.targetSelectionCalls() >= 2);
            assertTrue(timing.targetSelection().meanMs() > 0);
            assertTrue(timing.completeRound().meanMs() >= timing.targetSelection().meanMs());
        }
    }

    @Test
    void targetSelectionRunsBeforeIndependentMovementAndGoalsPersistBetweenSelections() {
        AtomicInteger selections = new AtomicInteger();
        List<Long> movementTicks = new ArrayList<>();
        List<java.util.Map<String, BotGoal>> received = new ArrayList<>();
        String moving = SCENE.replace("3: boat dark human", "3: boat dark human [heading: 90, speed: 6]");
        BotTestRun run = BotScenario.parse(moving).withTargetSelection(() -> (s, tick) -> {
            selections.incrementAndGet();
            return s.observerId().equals("light-S1") ? "dark-S3" : s.standardTargetId();
        }).run(() -> (s, tick, goals) -> {
            assertTrue(selections.get() >= 2, "ALL initial selections precede the first movement callback");
            assertEquals(java.util.Set.of("light-S1", "dark-S2"), goals.keySet());
            BotGoal goal = goals.get("light-S1");
            assertEquals("dark-S3", goal.targetShipId());
            assertEquals(BotGoal.Kind.ENEMY, goal.kind());
            VehicleSnapshot target = s.ships().stream().filter(ship -> ship.id().equals("dark-S3")).findFirst().orElseThrow();
            assertEquals(target.x(), goal.x(), .001);
            assertThrows(UnsupportedOperationException.class, goals::clear);
            movementTicks.add(tick);
            received.add(goals);
            // Own controller consumes only the assigned goal, without calling runStandard().
            VehicleSnapshot own = s.ships().get(0);
            double direction = Math.atan2(goal.x() - own.x(), goal.z() - own.z());
            s.steer(own.id(), 5, (int) Math.round(Math.toDegrees(direction - own.heading())));
        }, 30);
        assertEquals(30, movementTicks.size());
        assertTrue(selections.get() < movementTicks.size());
        assertFalse(run.targetDecisions().isEmpty());
        assertTrue(received.get(29).get("light-S1").x() > received.get(0).get("light-S1").x());
        assertTrue(run.displacement('1') > 0);
    }

    @Test
    void noEnemyProducesAnExplicitPatrolGoalForCustomMovement() {
        BotScenario.parse(SCENE).withTargetSelection(() -> (s, tick) -> null)
                .run(() -> (s, tick, goals) -> {
                    assertEquals(BotGoal.Kind.PATROL, goals.get("light-S1").kind());
                    assertNull(goals.get("light-S1").targetShipId());
                }, 2);
    }

    @Test
    void exportsDecisionsAlongsideReplay(@TempDir Path output) throws Exception {
        BotTestRun run = BotScenario.parse(SCENE).withTargetSelection(NearestTargetSelection::new)
                .run(BotCalculation::standard, 3);
        run.writeReport(output);
        String choices = Files.readString(output.resolve("targets.csv"));
        assertTrue(choices.contains("light-S1,,dark-S2"));
        assertTrue(Files.readString(output.resolve("index.html")).contains("Letzte Zielwahl"));
    }
}
