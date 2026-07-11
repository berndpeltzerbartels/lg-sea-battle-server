package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameStateServiceTest {

    @Test
    void fireTorpedoReturnsFreshSnapshot() {
        GameStateService service = new GameStateService(
                new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(),
                new NavigationService()
        );
        GameSnapshot before = service.snapshot();

        GameSnapshot after = service.fireTorpedo(new FireTorpedoRequest("player-BP-test", "light"));

        assertEquals(before.torpedoes().size() + 1, after.torpedoes().size());
        assertEquals(after.torpedoes().size(), service.snapshot().torpedoes().size());
    }
}
