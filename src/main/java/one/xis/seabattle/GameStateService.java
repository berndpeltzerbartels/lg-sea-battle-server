package one.xis.seabattle;

import one.xis.context.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GameStateService {

    private volatile GameSession session;
    private final DefaultGameSetupFactory setupFactory;
    private final RadarService radarService;
    private final NavigationService navigationService;
    private final Set<String> requestedTeamIds = new LinkedHashSet<>();
    private volatile PublishedGameSnapshots publishedSnapshots;
    private String setupId = "default";

    public GameStateService(DefaultGameSetupFactory setupFactory, RadarService radarService, NavigationService navigationService) {
        this.setupFactory = setupFactory;
        this.radarService = radarService;
        this.navigationService = navigationService;
        this.session = new GameSession(setupFactory.defaultSetup());
        this.publishedSnapshots = new PublishedGameSnapshots(session.snapshot());
    }

    public WorldMap worldMap() {
        return session.worldMap();
    }

    public GameSnapshot snapshot() {
        return publishedSnapshots.current();
    }

    public synchronized GameSnapshot tick(double deltaSeconds) {
        session.update(deltaSeconds, radarService, navigationService, session.worldMap());
        return publishedSnapshots.publish(session.snapshot());
    }

    public GameSnapshot updatePlayerState(PlayerStateUpdate update) {
        activateTeam(update.teamId());
        synchronized (this) {
            session.applyPlayerState(update, navigationService, session.worldMap());
            return publishedSnapshots.current();
        }
    }

    public GameSnapshot fireTorpedo(FireTorpedoRequest request) {
        activateTeam(request.teamId());
        synchronized (this) {
            session.applyFireTorpedo(request);
            return publishedSnapshots.current();
        }
    }

    public synchronized void releasePlayer(String playerId) {
        session.releasePlayer(playerId);
        publishedSnapshots.publish(session.snapshot());
    }

    public synchronized GameSnapshot reset(ResetGameRequest request) {
        if (request == null || !"bernd".equals(request.adminKey())) {
            throw new IllegalArgumentException("Reset is only available to the host.");
        }
        setupId = request.setupId();
        requestedTeamIds.clear();
        session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
        publishedSnapshots = new PublishedGameSnapshots(session.snapshot());
        return publishedSnapshots.current();
    }

    public RadarSnapshot radar(RadarRequest request) {
        activateTeam(request.teamId());
        return session.radar(request, radarService, session.worldMap());
    }

    public synchronized void activateTeam(String teamId) {
        if (!setupFactory.isKnownTeam(teamId) || !setupFactory.isPublicTeam(teamId) || requestedTeamIds.contains(teamId)) {
            return;
        }
        requestedTeamIds.add(teamId);
        session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
        publishedSnapshots = new PublishedGameSnapshots(session.snapshot());
    }
}
