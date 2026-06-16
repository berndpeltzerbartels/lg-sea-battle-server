package one.xis.seabattle;

import one.xis.context.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GameStateService {

    private GameSession session;
    private final DefaultGameSetupFactory setupFactory;
    private final RadarService radarService;
    private final NavigationService navigationService;
    private final Set<String> requestedTeamIds = new LinkedHashSet<>();
    private String setupId = "default";

    public GameStateService(DefaultGameSetupFactory setupFactory, RadarService radarService, NavigationService navigationService) {
        this.setupFactory = setupFactory;
        this.radarService = radarService;
        this.navigationService = navigationService;
        this.session = new GameSession(setupFactory.defaultSetup());
    }

    public WorldMap worldMap() {
        return session.worldMap();
    }

    public GameSnapshot snapshot() {
        return session.snapshot();
    }

    public GameSnapshot tick(double deltaSeconds) {
        session.update(deltaSeconds, radarService, navigationService, session.worldMap());
        return session.snapshot();
    }

    public GameSnapshot updatePlayerState(PlayerStateUpdate update) {
        activateTeam(update.teamId());
        return session.updatePlayerState(update, navigationService, session.worldMap());
    }

    public GameSnapshot fireTorpedo(FireTorpedoRequest request) {
        activateTeam(request.teamId());
        return session.fireTorpedo(request);
    }

    public void releasePlayer(String playerId) {
        session.releasePlayer(playerId);
    }

    public GameSnapshot reset(ResetGameRequest request) {
        if (request == null || !"bernd".equals(request.adminKey())) {
            throw new IllegalArgumentException("Reset is only available to the host.");
        }
        setupId = request.setupId();
        requestedTeamIds.clear();
        session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
        return session.snapshot();
    }

    public RadarSnapshot radar(RadarRequest request) {
        activateTeam(request.teamId());
        return session.radar(request, radarService, session.worldMap());
    }

    public synchronized void activateTeam(String teamId) {
        if (!setupFactory.isKnownTeam(teamId) || requestedTeamIds.contains(teamId)) {
            return;
        }
        requestedTeamIds.add(teamId);
        session = new GameSession(setupFactory.setup(setupId, List.copyOf(requestedTeamIds)));
    }
}
