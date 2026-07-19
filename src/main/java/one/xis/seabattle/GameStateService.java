package one.xis.seabattle;

import one.xis.context.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class GameStateService {

    private static final Logger LOGGER = Logger.getLogger(GameStateService.class.getName());
    private static final long TICK_MILLIS = 100;
    private static final double TICK_SECONDS = TICK_MILLIS / 1000.0;
    private static final double SLOW_TICK_LOG_THRESHOLD_MS = 80.0;
    private static final long TICK_METRICS_LOG_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private volatile GameSession session;
    private final DefaultGameSetupFactory setupFactory;
    private final RadarService radarService;
    private final NavigationService navigationService;
    private final Set<String> requestedTeamIds = new LinkedHashSet<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sea-battle-game-state");
        thread.setDaemon(true);
        return thread;
    });
    private volatile PublishedGameModel publishedModel;
    private String setupId = "default";
    private long tickMetricsStartedAtNanos = System.nanoTime();
    private long measuredTicks;
    private double measuredTickMillisTotal;
    private double measuredTickMillisMax;
    private long measuredSlowTicks;

    public GameStateService(DefaultGameSetupFactory setupFactory, RadarService radarService, NavigationService navigationService) {
        this.setupFactory = setupFactory;
        this.radarService = radarService;
        this.navigationService = navigationService;
        this.session = new GameSession(setupFactory.defaultSetup());
        this.publishedModel = publishCurrentModel();
        executor.execute(this::runTickLoop);
    }

    public WorldMap worldMap() {
        return session.worldMap();
    }

    public List<Vector2> respawnCandidates() {
        return session.respawnCandidates();
    }

    public GameSnapshot snapshot() {
        return publishedModel.state();
    }

    public GameSnapshot tick(double deltaSeconds) {
        SessionView view;
        synchronized (this) {
            session.update(deltaSeconds, radarService, navigationService, session.worldMap());
            view = captureSessionView();
        }
        publishModel(view);
        return view.state();
    }

    public GameSnapshot updatePlayerState(PlayerStateUpdate update) {
        activateTeam(update.teamId());
        synchronized (this) {
            session.applyPlayerState(update, navigationService, session.worldMap());
            return publishedModel.state();
        }
    }

    public GameSnapshot fireTorpedo(FireTorpedoRequest request) {
        SessionView view;
        activateTeam(request.teamId());
        synchronized (this) {
            session.applyFireTorpedo(request);
            view = captureSessionView();
        }
        publishModel(view);
        return view.state();
    }

    public GameSnapshot dropBomb(BombDropRequest request) {
        SessionView view;
        activateTeam(request.teamId());
        synchronized (this) {
            session.applyDropBomb(request);
            view = captureSessionView();
        }
        publishModel(view);
        return view.state();
    }

    public void releasePlayer(String playerId) {
        SessionView view;
        synchronized (this) {
            session.releasePlayer(playerId);
            view = captureSessionView();
        }
        publishModel(view);
    }

    public GameSnapshot reset(ResetGameRequest request) {
        if (request == null || !"bernd".equals(request.adminKey())) {
            throw new IllegalArgumentException("Reset is only available to the host.");
        }
        return resetToSetup(request.setupId());
    }

    public GameSnapshot resetToSetup(String nextSetupId) {
        SessionView view;
        synchronized (this) {
            setupId = nextSetupId;
            requestedTeamIds.clear();
            session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
            view = captureSessionView();
        }
        publishModel(view);
        return view.state();
    }

    public GameSnapshot resetCurrentSetup() {
        return resetToSetup(setupId);
    }

    public void activateTeam(String teamId) {
        SessionView view;
        synchronized (this) {
            if (!setupFactory.isKnownTeam(teamId) || !setupFactory.isPublicTeam(teamId) || requestedTeamIds.contains(teamId)) {
                return;
            }
            requestedTeamIds.add(teamId);
            session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
            view = captureSessionView();
        }
        publishModel(view);
    }

    private SessionView captureSessionView() {
        return new SessionView(session.snapshot(), session.worldMap());
    }

    private void publishModel(SessionView view) {
        publishedModel = buildPublishedModel(view.state());
    }

    private PublishedGameModel publishCurrentModel() {
        return buildPublishedModel(session.snapshot());
    }

    /*
     * Keep the published model independent of player-specific radar views. The
     * client already receives the full snapshot and renders its own radar from it;
     * precomputing one radar image per player made publishing scale with clients.
     */
    private PublishedGameModel buildPublishedModel(GameSnapshot state) {
        return new PublishedGameModel(state);
    }

    private record SessionView(GameSnapshot state, WorldMap worldMap) {
    }

    private void runTickLoop() {
        long periodNanos = TimeUnit.MILLISECONDS.toNanos(TICK_MILLIS);
        long nextStart = System.nanoTime() + periodNanos;
        long previousStart = System.nanoTime();
        while (!Thread.currentThread().isInterrupted()) {
            long now = System.nanoTime();
            if (now < nextStart) {
                sleepNanos(nextStart - now);
            }

            long started = System.nanoTime();
            double deltaSeconds = Math.max(TICK_SECONDS, (started - previousStart) / 1_000_000_000.0);
            previousStart = started;
            safeTick(deltaSeconds);

            long completed = System.nanoTime();
            recordTickDuration(started, completed);
            nextStart += periodNanos;
            while (nextStart < completed) {
                nextStart += periodNanos;
            }
        }
    }

    private void safeTick(double deltaSeconds) {
        try {
            tick(deltaSeconds);
        } catch (RuntimeException exception) {
            System.err.println("Sea Battle game tick failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
        }
    }

    private void sleepNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordTickDuration(long startedNanos, long completedNanos) {
        double elapsedMillis = (completedNanos - startedNanos) / 1_000_000.0;
        measuredTicks += 1;
        measuredTickMillisTotal += elapsedMillis;
        measuredTickMillisMax = Math.max(measuredTickMillisMax, elapsedMillis);
        if (elapsedMillis > SLOW_TICK_LOG_THRESHOLD_MS) {
            measuredSlowTicks += 1;
        }
        if (completedNanos - tickMetricsStartedAtNanos >= TICK_METRICS_LOG_INTERVAL_NANOS) {
            logAndResetTickMetrics(completedNanos);
        }
    }

    private void logAndResetTickMetrics(long nowNanos) {
        if (measuredTicks == 0) {
            tickMetricsStartedAtNanos = nowNanos;
            return;
        }
        double seconds = (nowNanos - tickMetricsStartedAtNanos) / 1_000_000_000.0;
        double averageMillis = measuredTickMillisTotal / measuredTicks;
        String message = String.format(
                Locale.ROOT,
                "Sea Battle game tick metrics: ticks=%d, seconds=%.1f, avg=%.2f ms, max=%.2f ms, slowTicks=%d, threshold=%.0f ms, setup=%s",
                measuredTicks,
                seconds,
                averageMillis,
                measuredTickMillisMax,
                measuredSlowTicks,
                SLOW_TICK_LOG_THRESHOLD_MS,
                setupId
        );
        if (measuredSlowTicks > 0) {
            LOGGER.warning(message);
        } else {
            LOGGER.info(message);
        }
        tickMetricsStartedAtNanos = nowNanos;
        measuredTicks = 0;
        measuredTickMillisTotal = 0;
        measuredTickMillisMax = 0;
        measuredSlowTicks = 0;
    }
}
