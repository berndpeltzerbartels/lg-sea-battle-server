package one.xis.seabattle.game;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CrewKillBrowserFixtureTest {
    @Test
    void exportActualGunnerKillsForBrowserStreamTest() throws Exception {
        for (boolean cannon : List.of(false, true)) {
            WorldMap world = new WorldMap(99002, List.of());
            GameSession session = new GameSession(new GameSetup("crew-kill-browser", world, List.of(
                    new FleetSetup("light", List.of(new ShipSetup("boat", "light", new Vector2(-50, 0),
                            0, "player-CAP-test", 2, 0, 999, "torpedo-boat", 0))),
                    new FleetSetup("dark", List.of(new ShipSetup("sub", "dark", new Vector2(0, 0),
                            0, "player-ANNA-test", 2, 0, 999, "submarine", 0)))), List.of()));
            GameSnapshot before = session.snapshot();
            FlakFireRequest shot = new FlakFireRequest("player-CAP-test", "light", "boat", -15, 3, 6,
                    300, -48, 0);
            if (cannon) session.applyFireCannon(shot, "player-GUN-test");
            else session.applyFireFlak(shot, "player-GUN-test");
            for (int i = 0; i < 20; i++) session.update(.01, new RadarService(), new NavigationService(), world);
            GameSnapshot after = session.snapshot();
            assertEquals(1, after.flakHits().size());
            assertEquals("boat", after.flakHits().get(0).shipId());
            assertEquals("sub", after.flakHits().get(0).targetShipId());
            assertEquals("sunk", after.ships().stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow().state());
            Path output = Path.of("build/test-fixtures/crew-kill-" + (cannon ? "cannon" : "flak") + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, new Gson().toJson(Map.of("before", before, "after", after)));
        }
    }
}
