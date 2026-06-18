package one.xis.seabattle;

import one.xis.context.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class GameStateService {

    private static final long TICK_MILLIS = 100;
    private static final double TICK_SECONDS = TICK_MILLIS / 1000.0;

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
        activateTeam(request.teamId());
        synchronized (this) {
            session.applyFireTorpedo(request);
            return publishedModel.state();
        }
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
        SessionView view;
        synchronized (this) {
            setupId = request.setupId();
            requestedTeamIds.clear();
            session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
            view = captureSessionView();
        }
        publishModel(view);
        return view.state();
    }

    public RadarSnapshot radar(RadarRequest request) {
        activateTeam(request.teamId());
        PublishedGameModel model = publishedModel;
        return radarFromSnapshot(request, model.state(), session.worldMap());
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

    private RadarSnapshot radarFromSnapshot(RadarRequest request, GameSnapshot state, WorldMap worldMap) {
        SpatialIndex<ShipSnapshot> shipIndex = SpatialIndex.from(
                state.ships(),
                ship -> new Vector2(ship.x(), ship.z()),
                radarService.range() / 3.0
        );
        ShipSnapshot observer = observerFor(request, state);
        if (observer == null) {
            return new RadarSnapshot("radar", state.sessionId(), state.t(), "", request.teamId(), 0, 0, 0, radarService.range(), List.of());
        }
        Vector2 observerPosition = new Vector2(observer.x(), observer.z());
        List<RadarContact> contacts = shipIndex.near(observerPosition, radarService.range()).stream()
                .filter(contact -> radarService.isVisible(observer, contact, worldMap))
                .map(contact -> radarContact(observer, contact))
                .toList();
        return new RadarSnapshot(
                "radar",
                state.sessionId(),
                state.t(),
                observer.id(),
                observer.teamId(),
                MathSupport.round(observer.x()),
                MathSupport.round(observer.z()),
                MathSupport.round(observer.heading()),
                MathSupport.round(radarService.range()),
                contacts
        );
    }

    private ShipSnapshot observerFor(RadarRequest request, GameSnapshot state) {
        return state.ships().stream()
                .filter(ship -> request.teamId().equals(ship.teamId()))
                .filter(ship -> request.playerId().equals(ship.controlledBy()))
                .findFirst()
                .or(() -> state.ships().stream()
                        .filter(ship -> request.teamId().equals(ship.teamId()))
                        .filter(ship -> "active".equals(ship.state()))
                        .findFirst())
                .orElse(null);
    }

    private RadarContact radarContact(ShipSnapshot observer, ShipSnapshot contact) {
        double dx = contact.x() - observer.x();
        double dz = contact.z() - observer.z();
        double bearing = MathSupport.normalizeAngle(Math.atan2(dx, dz) - observer.heading());
        return new RadarContact(
                contact.id(),
                contact.teamId(),
                MathSupport.round(contact.x()),
                MathSupport.round(contact.z()),
                MathSupport.round(contact.heading()),
                MathSupport.round(new Vector2(observer.x(), observer.z()).distanceTo(new Vector2(contact.x(), contact.z()))),
                MathSupport.round(bearing)
        );
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
}
