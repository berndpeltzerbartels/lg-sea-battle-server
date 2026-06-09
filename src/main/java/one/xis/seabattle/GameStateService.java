package one.xis.seabattle;

import one.xis.context.Service;

@Service
public class GameStateService {

    private GameSession session;
    private final DefaultGameSetupFactory setupFactory;
    private final RadarService radarService;
    private final NavigationService navigationService;

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
        return session.updatePlayerState(update, navigationService, session.worldMap());
    }

    public GameSnapshot fireTorpedo(FireTorpedoRequest request) {
        return session.fireTorpedo(request);
    }

    public void releasePlayer(String playerId) {
        session.releasePlayer(playerId);
    }

    public GameSnapshot reset(ResetGameRequest request) {
        if (request == null || !"bernd".equals(request.adminKey())) {
            throw new IllegalArgumentException("Reset is only available to the host.");
        }
        session = new GameSession(setupFactory.setup(request.setupId()));
        return session.snapshot();
    }

    public RadarSnapshot radar(RadarRequest request) {
        return session.radar(request, radarService, session.worldMap());
    }
}
