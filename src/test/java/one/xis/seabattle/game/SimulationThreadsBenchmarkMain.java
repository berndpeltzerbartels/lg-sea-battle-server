package one.xis.seabattle.game;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Standalone server benchmark: identical rounds, alternating thread counts, no HTTP or live-session access. */
public final class SimulationThreadsBenchmarkMain {
    public static void main(String[] args) {
        boolean bots = args.length > 0 && "bots".equals(args[0]);
        System.out.println("MODE " + (bots ? "bot-navigation-stream" : "movement"));
        var factory = new DefaultGameSetupFactory(new WorldMapService());
        GameSetup normal = factory.defaultSetup();
        for (GameSetup setup : List.of(normal, largerFleet(normal))) {
            int ships = setup.fleets().stream().mapToInt(fleet -> fleet.ships().size()).sum();
            System.out.printf("SCENARIO ships=%d terrain=%d%n", ships, setup.worldMap().landmasses().size());
            // Warm up both paths before collecting results. ABBA order reduces ordering bias.
            run(setup, 1, false, bots);
            run(setup, 2, false, bots);
            for (int threads : new int[]{1, 2, 2, 1, 1, 2, 2, 1}) run(setup, threads, true, bots);
        }
    }

    private static GameSetup largerFleet(GameSetup original) {
        var fleets = new ArrayList<FleetSetup>();
        for (FleetSetup fleet : original.fleets()) {
            var ships = new ArrayList<>(fleet.ships());
            for (ShipSetup ship : fleet.ships()) {
                ships.add(new ShipSetup(ship.id() + "-extra", ship.teamId(),
                        ship.position().add(new Vector2(0, 80)), ship.heading(), ship.controlledBy(),
                        ship.engineOrder(), ship.rudderDegrees(), ship.nextFireDelaySeconds(), ship.vehicleType(), ship.y()));
            }
            fleets.add(new FleetSetup(fleet.teamId(), ships));
        }
        return new GameSetup(original.id() + "-double", original.worldMap(), fleets, original.respawnCandidates());
    }

    private static void run(GameSetup setup, int threads, boolean report, boolean bots) {
        var session = new GameSession(setup, bots ? 1 : threads, bots && threads == 2);
        var radar = new RadarService();
        var navigation = new NavigationService();
        double[] durations = new double[1000];
        for (int tick = 0; tick < 1200; tick++) {
            long start = System.nanoTime();
            session.update(.1, radar, navigation, setup.worldMap());
            if (tick >= 200) durations[tick - 200] = (System.nanoTime() - start) / 1_000_000.0;
        }
        if (report) {
            Arrays.sort(durations);
            System.out.printf(Locale.ROOT,
                    "threads=%d ticks=%d avg=%.4fms p50=%.4fms p95=%.4fms p99=%.4fms max=%.4fms checksum=%d%n",
                    threads, durations.length, Arrays.stream(durations).average().orElseThrow(),
                    durations[499], durations[949], durations[989], durations[999], session.snapshot().ships().hashCode());
        }
    }
}
