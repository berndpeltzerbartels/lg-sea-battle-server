package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionPerformanceTest {

    private static final double MAX_TICK_MILLIS_AFTER_WARMUP = 10.0;
    private static final double MAX_P95_TICK_MILLIS_AFTER_WARMUP = 3.0;
    private static final int ENGINE_HALF = 5;
    private static final int ENGINE_TWO_THIRDS = 6;

    private final RadarService radarService = new RadarService();
    private final NavigationService navigationService = new NavigationService();

    @Test
    void widelySpreadBotTicksStayBelowBudgetAfterWarmup() {
        assertTickBudget("widely-spread-bots", spreadOutSetup());
    }

    @Test
    void crowdedBotTicksStayBelowBudgetAfterWarmup() {
        assertTickBudget("crowded-bots", crowdedSetup());
    }

    @Test
    void botPlaneAttackRunsStayBelowBudgetAfterWarmup() {
        assertTickBudget("bot-plane-attacks", botPlaneAttackSetup());
    }

    private void assertTickBudget(String scenario, GameSetup setup) {
        GameSession session = new GameSession(setup);
        int warmupTicks = 80;
        int measuredTicks = 320;

        for (int tick = 0; tick < warmupTicks; tick += 1) {
            session.update(0.1, radarService, navigationService, session.worldMap());
        }

        List<Double> tickMillis = new ArrayList<>();
        for (int tick = 0; tick < measuredTicks; tick += 1) {
            long started = System.nanoTime();
            session.update(0.1, radarService, navigationService, session.worldMap());
            tickMillis.add((System.nanoTime() - started) / 1_000_000.0);
        }

        List<Double> sorted = tickMillis.stream().sorted().toList();
        double average = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double p95 = percentile(sorted, 0.95);
        double max = sorted.get(sorted.size() - 1);
        System.out.printf(Locale.ROOT,
                "GameSession performance %-18s ticks=%d avg=%.3fms p95=%.3fms max=%.3fms budget=%.1fms%n",
                scenario,
                measuredTicks,
                average,
                p95,
                max,
                MAX_TICK_MILLIS_AFTER_WARMUP
        );
        assertTrue(p95 <= MAX_P95_TICK_MILLIS_AFTER_WARMUP,
                () -> scenario + " p95 tick " + p95 + "ms exceeded " + MAX_P95_TICK_MILLIS_AFTER_WARMUP + "ms");
        assertTrue(max <= MAX_TICK_MILLIS_AFTER_WARMUP,
                () -> scenario + " max tick " + max + "ms exceeded " + MAX_TICK_MILLIS_AFTER_WARMUP + "ms");
    }

    private GameSetup spreadOutSetup() {
        return setup("spread-out-performance", 800.0);
    }

    private GameSetup crowdedSetup() {
        return setup("crowded-performance", 70.0);
    }

    private GameSetup botPlaneAttackSetup() {
        List<ShipSetup> light = new ArrayList<>();
        List<ShipSetup> dark = new ArrayList<>();
        light.add(ship("light-human", "light", -90, 0, Math.PI / 2, "player-light", ENGINE_HALF, 0, 99, "torpedo-boat"));
        dark.add(ship("dark-human", "dark", 90, 0, -Math.PI / 2, "player-dark", ENGINE_HALF, 0, 99, "torpedo-boat"));

        for (int index = 0; index < 8; index += 1) {
            double z = (index - 3.5) * 44.0;
            light.add(ship("light-plane-" + (index + 1), "light", -220, z, Math.PI / 2, "bot", ENGINE_TWO_THIRDS, 0, index * 0.2, "scout-plane"));
            dark.add(ship("dark-plane-" + (index + 1), "dark", 220, z, -Math.PI / 2, "bot", ENGINE_TWO_THIRDS, 0, index * 0.2, "scout-plane"));
        }

        for (int index = 0; index < 6; index += 1) {
            double z = (index - 2.5) * 62.0;
            light.add(ship("light-boat-" + (index + 1), "light", -120, z, Math.PI / 2, "bot", ENGINE_HALF, 0, index * 0.35, "torpedo-boat"));
            dark.add(ship("dark-boat-" + (index + 1), "dark", 120, z, -Math.PI / 2, "bot", ENGINE_HALF, 0, index * 0.35, "torpedo-boat"));
        }

        return new GameSetup(
                "bot-plane-attack-performance",
                new WorldMap(9802, List.of()),
                List.of(
                        new FleetSetup("light", light),
                        new FleetSetup("dark", dark)
                ),
                List.of(new Vector2(-240, 0), new Vector2(240, 0))
        );
    }

    private GameSetup setup(String id, double spacing) {
        List<ShipSetup> light = new ArrayList<>();
        List<ShipSetup> dark = new ArrayList<>();
        for (int index = 0; index < 15; index += 1) {
            int row = index / 5;
            int col = index % 5;
            double x = (col - 2) * spacing;
            double z = (row - 1) * spacing;
            light.add(ship("light-" + (index + 1), "light", x - spacing * 0.45, z, Math.PI / 2, index));
            dark.add(ship("dark-" + (index + 1), "dark", x + spacing * 0.45, z, -Math.PI / 2, index));
        }
        return new GameSetup(
                id,
                new WorldMap(9801, List.of()),
                List.of(
                        new FleetSetup("light", light),
                        new FleetSetup("dark", dark)
                ),
                List.of(new Vector2(-spacing, 0), new Vector2(spacing, 0))
        );
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, int index) {
        return ship(
                id,
                teamId,
                x,
                z,
                heading,
                "bot",
                index % 2 == 0 ? ENGINE_HALF : ENGINE_TWO_THIRDS,
                0,
                index * 0.35,
                "torpedo-boat"
        );
    }

    private ShipSetup ship(String id, String teamId, double x, double z, double heading, String controlledBy,
                           int engineOrder, int rudderDegrees, double nextFireDelaySeconds, String vehicleType) {
        return new ShipSetup(
                id,
                teamId,
                new Vector2(x, z),
                MathSupport.normalizeAngle(heading),
                controlledBy,
                engineOrder,
                rudderDegrees,
                nextFireDelaySeconds,
                vehicleType
        );
    }

    private double percentile(List<Double> sorted, double ratio) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.floor((sorted.size() - 1) * ratio)));
    }
}
