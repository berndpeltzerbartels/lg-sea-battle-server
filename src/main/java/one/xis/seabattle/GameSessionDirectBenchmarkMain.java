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
            runRound(options.clients(), options.updateClients(), ticks, options.hz(), roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nWarmup round %d ignored.%n", round);
            } else {
                int measuredRound = round - options.warmupRounds();
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT, "%nMeasured round %d/%d: clients=%d, updateClients=%d, ticks=%d, hz=%d%n",
                        measuredRound, options.rounds(), options.clients(), options.updateClients(), ticks, options.hz());
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT, "%nCombined direct GameSession benchmark: clients=%d, updateClients=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                options.clients(), options.updateClients(), options.rounds(), ticks, options.hz());
        totalMetrics.print();
    }

    private void runShadowBenchmark(BenchmarkOptions options) {
        int ticks = options.durationSeconds() * options.hz();
        BenchmarkMetrics totalMetrics = new BenchmarkMetrics();

        for (int round = 1; round <= options.warmupRounds() + options.rounds(); round += 1) {
            boolean warmup = round <= options.warmupRounds();
            BenchmarkMetrics roundMetrics = new BenchmarkMetrics();
            runShadowRound(options.clients(), options.updateClients(), ticks, options.hz(), roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nShadow warmup round %d ignored.%n", round);
            } else {
                int measuredRound = round - options.warmupRounds();
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT, "%nShadow measured round %d/%d: clients=%d, updateClients=%d, ticks=%d, hz=%d%n",
                        measuredRound, options.rounds(), options.clients(), options.updateClients(), ticks, options.hz());
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT, "%nCombined shadow model benchmark: clients=%d, updateClients=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                options.clients(), options.updateClients(), options.rounds(), ticks, options.hz());
        totalMetrics.print();
    }

    private void runPublisherBenchmark(BenchmarkOptions options) throws Exception {
        int ticks = options.durationSeconds() * options.hz();
        BenchmarkMetrics totalMetrics = new BenchmarkMetrics();

        for (int round = 1; round <= options.warmupRounds() + options.rounds(); round += 1) {
            boolean warmup = round <= options.warmupRounds();
            BenchmarkMetrics roundMetrics = new BenchmarkMetrics();
            PublisherStats stats = runPublisherRound(options.clients(), options.updateClients(), ticks, options.hz(), roundMetrics);

            if (warmup) {
                System.out.printf(Locale.ROOT, "%nPublisher warmup round %d ignored. missedDeadlines=%d%n",
                        round, stats.missedDeadlines());
            } else {
                int measuredRound = round - options.warmupRounds();
                totalMetrics.addAll(roundMetrics);
                System.out.printf(Locale.ROOT,
                        "%nPublisher measured round %d/%d: clients=%d, updateClients=%d, ticks=%d, hz=%d, published=%d, missedDeadlines=%d%n",
                        measuredRound, options.rounds(), options.clients(), options.updateClients(), ticks, options.hz(),
                        stats.publishedModels(), stats.missedDeadlines());
                roundMetrics.print();
            }
        }

        System.out.printf(Locale.ROOT,
                "%nCombined publisher freshness benchmark: clients=%d, updateClients=%d, measuredRounds=%d, ticksPerRound=%d, hz=%d%n",
                options.clients(), options.updateClients(), options.rounds(), ticks, options.hz());
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

    private PublisherStats runPublisherRound(int clients, int updateClients, int ticks, int hz, BenchmarkMetrics metrics)
            throws Exception {
        GameSession session = new GameSession(new DefaultGameSetupFactory(new WorldMapService()).defaultSetup());
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
                session.update(tickSeconds, radarService, navigationService, session.worldMap());
                session.snapshot();

                long completed = System.nanoTime();
                metrics.add("publisher-build", (completed - started) / 1_000_000.0);
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
                VesselTypes.TORPEDO_BOAT,
                VesselTypes.SURFACE
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
                                    int warmupRounds, boolean shadow, boolean publisher) {

        static BenchmarkOptions from(String[] args) {
            int clients = integer("seaBattle.benchmark.clients", 30);
            int updateClients = integer("seaBattle.benchmark.updateClients", clients);
            int durationSeconds = integer("seaBattle.benchmark.durationSeconds", 20);
            int hz = integer("seaBattle.benchmark.hz", 4);
            int rounds = integer("seaBattle.benchmark.rounds", 3);
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
