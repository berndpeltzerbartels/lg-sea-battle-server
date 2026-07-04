package one.xis.seabattle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GameSessionDirectBenchmarkMain {

    private final RadarService radarService = new RadarService();
    private final NavigationService navigationService = new NavigationService();

    public static void main(String[] args) throws Exception {
        new GameSessionDirectBenchmarkMain().run(BenchmarkOptions.from(args));
    }

    void run(BenchmarkOptions options) throws Exception {
        if (options.publisher()) {
            runPublisherBenchmark(options);
        } else if (options.shadow()) {
            runShadowBenchmark(options);
        } else {
            runConcurrentBenchmark(options);
        }
    }

    private void runConcurrentBenchmark(BenchmarkOptions options) throws Exception {
        int ticks = options.durationSeconds() * options.hz();
        BenchmarkMetrics totalMetrics = new BenchmarkMetrics();

        for (int round = 1; round <= options.warmupRounds() + options.rounds(); round += 1) {
            boolean warmup = round <= options.warmupRounds();
            BenchmarkMetrics roundMetrics = new BenchmarkMetrics();
            runRound(options.clients(), options.updateClients(), options.shipsPerTeam(), ticks, options.hz(), roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nWarmup round %d ignored.%n", round);
            } else {
                int measuredRound = round - options.warmupRounds();
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT, "%nMeasured round %d/%d: clients=%d, updateClients=%d, shipsPerTeam=%d, ticks=%d, hz=%d%n",
                        measuredRound, options.rounds(), options.clients(), options.updateClients(), options.shipsPerTeam(), ticks, options.hz());
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT, "%nCombined direct GameSession benchmark: clients=%d, updateClients=%d, shipsPerTeam=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                options.clients(), options.updateClients(), options.shipsPerTeam(), options.rounds(), ticks, options.hz());
        totalMetrics.print();
    }

    private void runShadowBenchmark(BenchmarkOptions options) {
        int ticks = options.durationSeconds() * options.hz();
        BenchmarkMetrics totalMetrics = new BenchmarkMetrics();

        for (int round = 1; round <= options.warmupRounds() + options.rounds(); round += 1) {
            boolean warmup = round <= options.warmupRounds();
            BenchmarkMetrics roundMetrics = new BenchmarkMetrics();
            runShadowRound(options.clients(), options.updateClients(), options.shipsPerTeam(), ticks, options.hz(), roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nShadow warmup round %d ignored.%n", round);
            } else {
                int measuredRound = round - options.warmupRounds();
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT, "%nShadow measured round %d/%d: clients=%d, updateClients=%d, shipsPerTeam=%d, ticks=%d, hz=%d%n",
                        measuredRound, options.rounds(), options.clients(), options.updateClients(), options.shipsPerTeam(), ticks, options.hz());
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT, "%nCombined shadow model benchmark: clients=%d, updateClients=%d, shipsPerTeam=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                options.clients(), options.updateClients(), options.shipsPerTeam(), options.rounds(), ticks, options.hz());
        totalMetrics.print();
    }

    private void runPublisherBenchmark(BenchmarkOptions options) throws Exception {
        int ticks = options.durationSeconds() * options.hz();
        BenchmarkMetrics totalMetrics = new BenchmarkMetrics();

        for (int round = 1; round <= options.warmupRounds() + options.rounds(); round += 1) {
            boolean warmup = round <= options.warmupRounds();
            BenchmarkMetrics roundMetrics = new BenchmarkMetrics();
            PublisherStats stats = runPublisherRound(options.clients(), options.updateClients(), options.shipsPerTeam(), ticks, options.hz(), roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nPublisher warmup round %d ignored. missedDeadlines=%d%n",
                        round, stats.missedDeadlines());
            } else {
                int measuredRound = round - options.warmupRounds();
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT,
                        "%nPublisher measured round %d/%d: clients=%d, updateClients=%d, shipsPerTeam=%d, ticks=%d, hz=%d, published=%d, missedDeadlines=%d%n",
                        measuredRound, options.rounds(), options.clients(), options.updateClients(), options.shipsPerTeam(), ticks, options.hz(),
                        stats.publishedModels(), stats.missedDeadlines());
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT,
                "%nCombined publisher freshness benchmark: clients=%d, updateClients=%d, shipsPerTeam=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                options.clients(), options.updateClients(), options.shipsPerTeam(), options.rounds(), ticks, options.hz());
        totalMetrics.print();
    }

    private void runRound(int clients, int updateClients, int shipsPerTeam, int ticks, int hz, BenchmarkMetrics metrics) throws Exception {
        GameSession session = new GameSession(benchmarkSetup(shipsPerTeam));
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

    private void runShadowRound(int clients, int updateClients, int shipsPerTeam, int ticks, int hz, BenchmarkMetrics metrics) {
        GameSession session = new GameSession(benchmarkSetup(shipsPerTeam));
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

            metrics.measure("shadow-model-total", session::snapshot);
        }
    }

    private PublisherStats runPublisherRound(int clients, int updateClients, int shipsPerTeam, int ticks, int hz, BenchmarkMetrics metrics)
            throws Exception {
        GameSession session = new GameSession(benchmarkSetup(shipsPerTeam));
        List<BenchmarkClient> benchmarkClients = createClients(clients, session.snapshot());
        List<BenchmarkClient> activeClients = benchmarkClients.stream().limit(updateClients).toList();
        assignPlayers(session, activeClients);

        long periodNanos = 1_000_000_000L / hz;
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<PublishedModel> latest = new AtomicReference<>();

        Thread publisher = new Thread(() -> {
            long nextStart = System.nanoTime();
            long previousStart = nextStart;
            double simulationSeconds = 0;
            for (int tick = 1; tick <= ticks && running.get(); tick += 1) {
                long scheduledStart = nextStart;
                long now = System.nanoTime();
                if (now < scheduledStart) {
                    sleepNanos(scheduledStart - now);
                }

                long started = System.nanoTime();
                double tickSeconds = Math.max(1.0 / hz, (started - previousStart) / 1_000_000_000.0);
                simulationSeconds += tickSeconds;
                previousStart = started;

                double elapsedSeconds = simulationSeconds;
                activeClients.forEach(client -> session.updatePlayerState(
                        playerUpdate(client, elapsedSeconds),
                        navigationService,
                        session.worldMap()
                ));
                RadarService.VisibilityMetrics visibilityMetrics = new RadarService.VisibilityMetrics();
                RadarService.collectVisibilityMetrics(visibilityMetrics);
                try {
                    session.update(tickSeconds, radarService, navigationService, session.worldMap());
                } finally {
                    RadarService.clearVisibilityMetrics();
                }
                session.snapshot();

                long completed = System.nanoTime();
                metrics.add("publisher-build", (completed - started) / 1_000_000.0);
                metrics.add("visibility-total", visibilityMetrics.millis());
                metrics.add("visibility-calls", visibilityMetrics.calls());
                metrics.add("visibility-range-rejects", visibilityMetrics.range());
                metrics.add("visibility-land-rejects", visibilityMetrics.land());
                metrics.add("visibility-visible", visibilityMetrics.visible());
                metrics.add("publisher-deadline-lag", Math.max(0, (completed - (scheduledStart + periodNanos)) / 1_000_000.0));
                latest.set(new PublishedModel(tick, scheduledStart, completed));
                nextStart = scheduledStart + periodNanos;
                while (nextStart < completed) {
                    nextStart += periodNanos;
                    metrics.add("publisher-skipped-slot", 1);
                }
            }
            running.set(false);
        }, "sea-battle-publisher-benchmark");

        ExecutorService readers = Executors.newFixedThreadPool(Math.min(128, Math.max(8, clients)));
        long readersStarted = System.nanoTime();
        List<Runnable> operations = new ArrayList<>();
        for (int client = 0; client < clients; client += 1) {
            int clientIndex = client;
            operations.add(() -> {
                long nextRead = readersStarted + (periodNanos * clientIndex / Math.max(1, clients));
                long lastSequence = -1;
                for (int read = 0; read < ticks && running.get(); read += 1) {
                    long now = System.nanoTime();
                    if (now < nextRead) {
                        sleepNanos(nextRead - now);
                    }
                    long readStarted = System.nanoTime();
                    PublishedModel model = latest.get();
                    long readAt = System.nanoTime();
                    metrics.add("published-read", (readAt - readStarted) / 1_000_000.0);
                    if (model != null) {
                        metrics.add("published-age", (readAt - model.completedAtNanos()) / 1_000_000.0);
                        metrics.add("published-scheduled-age", (readAt - model.scheduledAtNanos()) / 1_000_000.0);
                        if (lastSequence >= 0) {
                            metrics.add("published-sequence-gap", Math.max(0, model.sequence() - lastSequence - 1));
                        }
                        lastSequence = model.sequence();
                    }
                    nextRead += periodNanos;
                }
            });
        }

        try {
            publisher.start();
            var futures = operations.stream().map(readers::submit).toList();
            publisher.join(TimeUnit.SECONDS.toMillis(Math.max(5, ticks / Math.max(1, hz) + 10)));
            running.set(false);
            for (var future : futures) {
                future.get();
            }
        } finally {
            running.set(false);
            publisher.join(TimeUnit.SECONDS.toMillis(2));
            readers.shutdown();
            readers.awaitTermination(5, TimeUnit.SECONDS);
        }

        long publishedModels = latest.get() == null ? 0 : latest.get().sequence();
        long missedDeadlines = metrics.values("publisher-deadline-lag").stream()
                .filter(value -> value > 0.0)
                .count();
        return new PublisherStats(publishedModels, missedDeadlines);
    }

    private void assignPlayers(GameSession session, List<BenchmarkClient> clients) {
        for (BenchmarkClient client : clients) {
            session.updatePlayerState(playerUpdate(client, 0), navigationService, session.worldMap());
        }
    }

    private GameSetup benchmarkSetup(int shipsPerTeam) {
        GameSetup base = new DefaultGameSetupFactory(new WorldMapService()).defaultSetup();
        List<FleetSetup> fleets = new ArrayList<>();
        for (int fleetIndex = 0; fleetIndex < base.fleets().size(); fleetIndex += 1) {
            FleetSetup fleet = base.fleets().get(fleetIndex);
            List<ShipSetup> ships = new ArrayList<>(fleet.ships());
            for (int index = ships.size(); index < shipsPerTeam; index += 1) {
                double heading = MathSupport.normalizeAngle((fleetIndex == 0 ? 1 : -1) * Math.PI / 2 + index * 0.11);
                Vector2 position = benchmarkShipPosition(base, ships, fleetIndex, index, heading);
                ships.add(new ShipSetup(
                        fleet.teamId() + "-" + (index + 1),
                        fleet.teamId(),
                        position,
                        heading,
                        "bot",
                        5,
                        0,
                        3 + index * 1.5
                ));
            }
            fleets.add(new FleetSetup(fleet.teamId(), ships.size() <= shipsPerTeam ? ships : ships.subList(0, shipsPerTeam)));
        }
        return new GameSetup(base.id() + "-benchmark-" + shipsPerTeam, base.worldMap(), fleets, base.respawnCandidates());
    }

    private Vector2 benchmarkShipPosition(GameSetup setup, List<ShipSetup> existingShips, int fleetIndex, int shipIndex, double heading) {
        for (int attempt = 0; attempt < setup.respawnCandidates().size() * 8; attempt += 1) {
            Vector2 base = setup.respawnCandidates().get(Math.floorMod(shipIndex * 5 + fleetIndex * 3 + attempt,
                    setup.respawnCandidates().size()));
            double angle = attempt * 1.27 + fleetIndex * 0.8;
            double radius = 80 + (attempt % 6) * 38;
            Vector2 candidate = base.add(new Vector2(Math.sin(angle) * radius, Math.cos(angle) * radius));
            boolean blocked = navigationService.isShipBlocked(candidate, heading, setup.worldMap());
            boolean crowded = existingShips.stream().anyMatch(ship -> ship.position().distanceTo(candidate) < 95);
            if (!blocked && !crowded) {
                return candidate;
            }
        }
        return setup.respawnCandidates().get(Math.floorMod(shipIndex + fleetIndex, setup.respawnCandidates().size()));
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

    private record PublishedModel(long sequence, long scheduledAtNanos, long completedAtNanos) {
    }

    private record PublisherStats(long publishedModels, long missedDeadlines) {
    }

    private record BenchmarkOptions(int clients, int updateClients, int durationSeconds, int hz, int rounds,
                                    int shipsPerTeam,
                                    int warmupRounds, boolean shadow, boolean publisher) {

        static BenchmarkOptions from(String[] args) {
            int clients = integer("seaBattle.benchmark.clients", 30);
            int updateClients = integer("seaBattle.benchmark.updateClients", 1);
            int durationSeconds = integer("seaBattle.benchmark.durationSeconds", 20);
            int hz = integer("seaBattle.benchmark.hz", 4);
            int rounds = integer("seaBattle.benchmark.rounds", 3);
            int shipsPerTeam = integer("seaBattle.benchmark.shipsPerTeam", 15);
            int warmupRounds = integer("seaBattle.benchmark.warmupRounds", 1);
            boolean shadow = Boolean.getBoolean("seaBattle.shadowBenchmark");
            boolean publisher = Boolean.getBoolean("seaBattle.publisherBenchmark");

            for (int index = 0; index < args.length; index += 1) {
                String arg = args[index];
                String next = index + 1 < args.length ? args[index + 1] : null;
                switch (arg) {
                    case "--clients" -> clients = parseInt(arg, next);
                    case "--update-clients" -> updateClients = parseInt(arg, next);
                    case "--duration" -> durationSeconds = parseInt(arg, next);
                    case "--hz" -> hz = parseInt(arg, next);
                    case "--rounds" -> rounds = parseInt(arg, next);
                    case "--ships-per-team" -> shipsPerTeam = parseInt(arg, next);
                    case "--warmup-rounds" -> warmupRounds = parseInt(arg, next);
                    case "--shadow" -> {
                        shadow = true;
                        continue;
                    }
                    case "--publisher" -> {
                        publisher = true;
                        continue;
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
                index += 1;
            }

            return new BenchmarkOptions(
                    clients,
                    Math.min(updateClients, clients),
                    durationSeconds,
                    hz,
                    rounds,
                    shipsPerTeam,
                    warmupRounds,
                    shadow,
                    publisher
            );
        }

        private static int integer(String name, int defaultValue) {
            return Integer.getInteger(name, defaultValue);
        }

        private static int parseInt(String name, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " needs a value");
            }
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return parsed;
        }
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
            add(name, elapsedMs);
        }

        void add(String name, double value) {
            latenciesByName.computeIfAbsent(name, ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
                    .add(value);
        }

        List<Double> values(String name) {
            return latenciesByName.getOrDefault(name, List.of());
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

    private static void sleepNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
