package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GameSessionDirectBenchmarkTest {

    private final RadarService radarService = new RadarService();
    private final NavigationService navigationService = new NavigationService();

    @Test
    void benchmarksConcurrentDirectSessionCalls() throws Exception {
        assumeTrue(Boolean.getBoolean("seaBattle.directBenchmark"),
                "Run with -DseaBattle.directBenchmark=true to execute the direct in-memory benchmark.");

        int clients = Integer.getInteger("seaBattle.benchmark.clients", 30);
        int updateClients = Math.min(Integer.getInteger("seaBattle.benchmark.updateClients", clients), clients);
        int durationSeconds = Integer.getInteger("seaBattle.benchmark.durationSeconds", 20);
        int hz = Integer.getInteger("seaBattle.benchmark.hz", 4);
        int measuredRounds = Integer.getInteger("seaBattle.benchmark.rounds", 3);
        int warmupRounds = Integer.getInteger("seaBattle.benchmark.warmupRounds", 1);
        int ticks = durationSeconds * hz;

        BenchmarkMetrics totalMetrics = new BenchmarkMetrics();

        for (int round = 1; round <= warmupRounds + measuredRounds; round += 1) {
            boolean warmup = round <= warmupRounds;
            BenchmarkMetrics roundMetrics = new BenchmarkMetrics();
            runRound(clients, updateClients, ticks, hz, roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nWarmup round %d ignored.%n", round);
            } else {
                int measuredRound = round - warmupRounds;
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT, "%nMeasured round %d/%d: clients=%d, updateClients=%d, ticks=%d, hz=%d%n",
                        measuredRound, measuredRounds, clients, Math.min(updateClients, clients), ticks, hz);
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT, "%nCombined direct GameSession benchmark: clients=%d, updateClients=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                clients, Math.min(updateClients, clients), measuredRounds, ticks, hz);
        totalMetrics.print();
    }

    @Test
    void benchmarksShadowModelBuildPerTick() {
        assumeTrue(Boolean.getBoolean("seaBattle.shadowBenchmark"),
                "Run with -DseaBattle.shadowBenchmark=true to execute the shadow model benchmark.");

        int clients = Integer.getInteger("seaBattle.benchmark.clients", 30);
        int updateClients = Math.min(Integer.getInteger("seaBattle.benchmark.updateClients", clients), clients);
        int durationSeconds = Integer.getInteger("seaBattle.benchmark.durationSeconds", 20);
        int hz = Integer.getInteger("seaBattle.benchmark.hz", 4);
        int measuredRounds = Integer.getInteger("seaBattle.benchmark.rounds", 3);
        int warmupRounds = Integer.getInteger("seaBattle.benchmark.warmupRounds", 1);
        int ticks = durationSeconds * hz;

        BenchmarkMetrics totalMetrics = new BenchmarkMetrics();

        for (int round = 1; round <= warmupRounds + measuredRounds; round += 1) {
            boolean warmup = round <= warmupRounds;
            BenchmarkMetrics roundMetrics = new BenchmarkMetrics();
            runShadowRound(clients, updateClients, ticks, hz, roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nShadow warmup round %d ignored.%n", round);
            } else {
                int measuredRound = round - warmupRounds;
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT, "%nShadow measured round %d/%d: clients=%d, updateClients=%d, ticks=%d, hz=%d%n",
                        measuredRound, measuredRounds, clients, updateClients, ticks, hz);
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT, "%nCombined shadow model benchmark: clients=%d, updateClients=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                clients, updateClients, measuredRounds, ticks, hz);
        totalMetrics.print();
    }

    private void runRound(int clients, int updateClients, int ticks, int hz, BenchmarkMetrics metrics) throws Exception {
        GameSession session = new GameSession(new DefaultGameSetupFactory(new WorldMapService()).defaultSetup());
        List<BenchmarkClient> benchmarkClients = createClients(clients, session.snapshot());
        List<BenchmarkClient> activeClients = benchmarkClients.stream().limit(updateClients).toList();
        assignPlayers(session, activeClients);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(128, Math.max(8, clients * 2)));
        try {
            for (int tick = 1; tick <= ticks; tick += 1) {
                double elapsedSeconds = (double) tick / hz;
                List<Runnable> operations = new ArrayList<>();

                for (BenchmarkClient client : benchmarkClients) {
                    operations.add(() -> metrics.measure("snapshot", session::snapshot));
                    operations.add(() -> metrics.measure("radar", () -> session.radar(
                            new RadarRequest(client.playerId(), client.teamId()),
                            radarService,
                            session.worldMap()
                    )));
                }

                for (BenchmarkClient client : activeClients) {
                    operations.add(() -> metrics.measure("player-state", () -> session.updatePlayerState(
                            playerUpdate(client, elapsedSeconds),
                            navigationService,
                            session.worldMap()
                    )));
                }

                var futures = operations.stream().map(executor::submit).toList();
                for (var future : futures) {
                    future.get();
                }
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void runShadowRound(int clients, int updateClients, int ticks, int hz, BenchmarkMetrics metrics) {
        GameSession session = new GameSession(new DefaultGameSetupFactory(new WorldMapService()).defaultSetup());
        List<BenchmarkClient> benchmarkClients = createClients(clients, session.snapshot());
        List<BenchmarkClient> activeClients = benchmarkClients.stream().limit(updateClients).toList();
        assignPlayers(session, activeClients);

        for (int tick = 1; tick <= ticks; tick += 1) {
            double elapsedSeconds = (double) tick / hz;

            metrics.measure("apply-player-commands", () -> activeClients.forEach(client -> session.updatePlayerState(
                    playerUpdate(client, elapsedSeconds),
                    navigationService,
                    session.worldMap()
            )));

            metrics.measure("game-tick", () -> session.update(1.0 / hz, radarService, navigationService, session.worldMap()));

            metrics.measure("shadow-state", session::snapshot);

            metrics.measure("shadow-radars", () -> benchmarkClients.forEach(client -> session.radar(
                    new RadarRequest(client.playerId(), client.teamId()),
                    radarService,
                    session.worldMap()
            )));

            metrics.measure("shadow-model-total", () -> {
                session.snapshot();
                benchmarkClients.forEach(client -> session.radar(
                        new RadarRequest(client.playerId(), client.teamId()),
                        radarService,
                        session.worldMap()
                ));
            });
        }
    }

    private void assignPlayers(GameSession session, List<BenchmarkClient> clients) {
        for (BenchmarkClient client : clients) {
            session.updatePlayerState(playerUpdate(client, 0, false), navigationService, session.worldMap());
        }
    }

    private List<BenchmarkClient> createClients(int count, GameSnapshot snapshot) {
        Map<String, List<ShipSnapshot>> shipsByTeam = snapshot.ships().stream()
                .collect(java.util.stream.Collectors.groupingBy(ShipSnapshot::teamId));
        List<BenchmarkClient> clients = new ArrayList<>();
        for (int index = 0; index < count; index += 1) {
            String teamId = index % 2 == 0 ? "light" : "dark";
            List<ShipSnapshot> teamShips = shipsByTeam.getOrDefault(teamId, List.of());
            ShipSnapshot ship = teamShips.get(Math.min(teamShips.size() - 1, index / 2));
            clients.add(new BenchmarkClient(
                    index,
                    "player-B" + String.format("%02d", index + 1) + "-direct",
                    teamId,
                    ship.x(),
                    ship.z(),
                    ship.heading()
            ));
        }
        return clients;
    }

    private PlayerStateUpdate playerUpdate(BenchmarkClient client, double elapsedSeconds) {
        double speed = 6.4;
        double heading = client.heading() + Math.sin(elapsedSeconds * 0.18 + client.index()) * 0.35;
        double distance = speed * elapsedSeconds;
        return new PlayerStateUpdate(
                client.playerId(),
                client.teamId(),
                client.startX() + Math.sin(heading) * distance,
                client.startZ() + Math.cos(heading) * distance,
                heading,
                speed,
                Math.sin(elapsedSeconds * 0.35 + client.index()) * 0.025,
                6,
                (int) Math.round(Math.sin(elapsedSeconds * 0.45 + client.index()) * 18),
                elapsedSeconds,
                false
        );
    }

    private record BenchmarkClient(int index, String playerId, String teamId, double startX, double startZ,
                                   double heading) {
    }

    private static final class BenchmarkMetrics {

        private final Map<String, List<Double>> latenciesByName = new ConcurrentHashMap<>();

        void addAll(BenchmarkMetrics metrics) {
            metrics.latenciesByName.forEach((name, values) -> latenciesByName
                    .computeIfAbsent(name, ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
                    .addAll(values));
        }

        void measure(String name, BenchmarkOperation operation) {
            long started = System.nanoTime();
            operation.run();
            double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
            latenciesByName.computeIfAbsent(name, ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
                    .add(elapsedMs);
        }

        void print() {
            System.out.println("operation        count    min     avg     std     p50     p95     p99     max");
            latenciesByName.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> print(entry.getKey(), entry.getValue()));
        }

        private void print(String name, List<Double> values) {
            List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
            double average = average(sorted);
            double std = standardDeviation(sorted, average);
            System.out.printf(Locale.ROOT,
                    "%-14s %6d %7.3f %7.3f %7.3f %7.3f %7.3f %7.3f %7.3f%n",
                    name,
                    sorted.size(),
                    sorted.get(0),
                    average,
                    std,
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95),
                    percentile(sorted, 0.99),
                    sorted.get(sorted.size() - 1)
            );
        }

        private double average(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        private double standardDeviation(List<Double> values, double average) {
            double variance = values.stream()
                    .mapToDouble(value -> Math.pow(value - average, 2))
                    .average()
                    .orElse(0);
            return Math.sqrt(variance);
        }

        private double percentile(List<Double> values, double ratio) {
            return values.get(Math.min(values.size() - 1, (int) Math.floor((values.size() - 1) * ratio)));
        }
    }

    @FunctionalInterface
    private interface BenchmarkOperation {
        void run();
    }
}
