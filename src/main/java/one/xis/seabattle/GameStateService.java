package one.xis.seabattle;

import one.xis.context.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private final Set<RadarKey> radarSubscriptions = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
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
        executor.scheduleAtFixedRate(this::safeTick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
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
        radarSubscriptions.add(new RadarKey(update.playerId(), update.teamId()));
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
        synchronized (this) {
            session.releasePlayer(playerId);
        }
        radarSubscriptions.removeIf(key -> key.playerId().equals(playerId));
    }

    public GameSnapshot reset(ResetGameRequest request) {
        if (request == null || !"bernd".equals(request.adminKey())) {
            throw new IllegalArgumentException("Reset is only available to the host.");
        }
        SessionView view;
        synchronized (this) {
            setupId = request.setupId();
            requestedTeamIds.clear();
            radarSubscriptions.clear();
            session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
            view = captureSessionView();
        }
        publishModel(view);
        return view.state();
    }

    public RadarSnapshot radar(RadarRequest request) {
        activateTeam(request.teamId());
        radarSubscriptions.add(RadarKey.from(request));
        PublishedGameModel model = publishedModel;
        RadarSnapshot radar = model.radar(request);
        return radar != null ? radar : emptyRadar(request, model.state());
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
        publishedModel = buildPublishedModel(view.state(), view.worldMap(), Set.copyOf(radarSubscriptions));
    }

    private PublishedGameModel publishCurrentModel() {
        return buildPublishedModel(session.snapshot(), session.worldMap(), Set.copyOf(radarSubscriptions));
    }

    /*
     * Build the public read model from immutable snapshots. Do not call
     * GameSession.radar(...) here: GameSession is synchronized and mutable, so that
     * would put the expensive radar work back onto the session lock.
     */
    private PublishedGameModel buildPublishedModel(GameSnapshot state, WorldMap worldMap, Set<RadarKey> subscriptions) {
        Map<RadarKey, RadarSnapshot> radars = new LinkedHashMap<>();
        for (RadarKey key : subscriptions) {
            RadarRequest request = new RadarRequest(key.playerId(), key.teamId());
            radars.put(key, radarFromSnapshot(request, state, worldMap));
        }
        return new PublishedGameModel(state, Map.copyOf(radars));
    }

    private RadarSnapshot radarFromSnapshot(RadarRequest request, GameSnapshot state, WorldMap worldMap) {
        ShipSnapshot observer = observerFor(request, state);
        if (observer == null) {
            return new RadarSnapshot("radar", state.sessionId(), state.t(), "", request.teamId(), 0, 0, 0, radarService.range(), List.of());
        }
        List<RadarContact> contacts = state.ships().stream()
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

    private void safeTick() {
        try {
            tick(TICK_SECONDS);
        } catch (RuntimeException exception) {
            System.err.println("Sea Battle game tick failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
        }
    }

    private RadarSnapshot emptyRadar(RadarRequest request, GameSnapshot state) {
        ShipSnapshot observer = observerFor(request, state);
        if (observer == null) {
            return new RadarSnapshot("radar", state.sessionId(), state.t(), "", request.teamId(), 0, 0, 0, radarService.range(), List.of());
        }
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
                List.of()
        );
    }
}
