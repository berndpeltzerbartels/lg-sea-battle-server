package one.xis.seabattle;

import one.xis.context.Service;

@Service
public class GameStateService {

    private GameSession session = new GameSession();
    private final WorldMapService worldMapService;
    private final RadarService radarService;
    private final NavigationService navigationService;

    public GameStateService(WorldMapService worldMapService, RadarService radarService, NavigationService navigationService) {
        this.worldMapService = worldMapService;
        this.radarService = radarService;
        this.navigationService = navigationService;
    }

    public GameSnapshot snapshot() {
        return session.snapshot();
    }

    public GameSnapshot tick(double deltaSeconds) {
        session.update(deltaSeconds, radarService, navigationService, worldMapService.world());
        return session.snapshot();
    }

    public GameSnapshot updatePlayerState(PlayerStateUpdate update) {
        return session.updatePlayerState(update, navigationService, worldMapService.world());
    }

    public GameSnapshot fireTorpedo(FireTorpedoRequest request) {
        return session.fireTorpedo(request);
    }

    public GameSnapshot reset(ResetGameRequest request) {
        if (request == null || !"bernd".equals(request.adminKey())) {
            throw new IllegalArgumentException("Reset is only available to the host.");
        }
        session = new GameSession();
        return session.snapshot();
    }

    public RadarSnapshot radar(RadarRequest request) {
        return session.radar(request, radarService, worldMapService.world());
    }
}
