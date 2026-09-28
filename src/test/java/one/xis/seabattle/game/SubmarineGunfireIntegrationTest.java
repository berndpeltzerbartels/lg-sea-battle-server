package one.xis.seabattle.game;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubmarineGunfireIntegrationTest {
    private static final double SCALE = SeaBattleGameConfig.TORPEDO_BOAT_SCALE;
    private static final double DEPTH = -1.86 * SCALE;

    // Model-space points taken from submarineModel.js, not the server collision volumes.
    private record Aim(String name, double y, double z, boolean hit) {}

    @TestFactory
    List<DynamicTest> nearShotsCoverHullEndsTowerAndPeriscopes() {
        List<Aim> aims = List.of(
                new Aim("stern tip", .2, -5.0, true),
                new Aim("aft hull", .2, -4.5, true),
                new Aim("mid hull", .2, -2, true),
                new Aim("forward hull", .2, 2, true),
                new Aim("bow", .2, 4.5, true),
                new Aim("bow tip", .2, 5.0, true),
                new Aim("tower centre", 1.0, -.3, true),
                new Aim("tower upper front wall", 1.38, .5, true),
                new Aim("tower upper rear wall", 1.24, -.65, true),
                new Aim("above front rim", 1.52, .5, false),
                new Aim("above lower rear rim", 1.40, -.65, false),
                new Aim("deck casing", .66, 2, true),
                new Aim("first periscope", 1.92, .022, true),
                new Aim("second periscope", 1.92, .184, true),
                new Aim("past stern", .2, -5.15, false),
                new Aim("past bow", .2, 5.15, false),
                new Aim("above hull beside tower", .85, 2, false),
                new Aim("between periscopes", 1.92, .10, false));
        List<DynamicTest> tests = new ArrayList<>();
        for (boolean cannon : List.of(false, true)) {
            for (double heading : new double[]{0, Math.PI / 2}) {
                for (int side : new int[]{-1, 1}) {
                    for (Aim aim : aims) {
                        String name = (cannon ? "cannon" : "flak") + " heading=" + heading + " side=" + side + " " + aim.name();
                        tests.add(DynamicTest.dynamicTest(name, () -> verifyShot(cannon, 15, heading, side, aim)));
                    }
                }
            }
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> distantHullShotsContinueThroughWater() {
        List<DynamicTest> tests = new ArrayList<>();
        for (boolean cannon : List.of(false, true)) {
            for (double distance : new double[]{100, 200}) {
                tests.add(DynamicTest.dynamicTest("underwater hull cannon=" + cannon + " distance=" + distance,
                        () -> verifyShot(cannon, distance, 0, -1, new Aim("forward hull beyond former water range", .2, 2, true))));
            }
        }
        return tests;
    }

    private void verifyShot(boolean cannon, double distance, double heading, int side, Aim aim) {
        WorldMap map = new WorldMap(99002, List.of());
        NavigationService navigation = new NavigationService();
        double cos = Math.cos(heading), sin = Math.sin(heading);
        double startX = side * distance * cos + aim.z() * SCALE * sin;
        double startZ = -side * distance * sin + aim.z() * SCALE * cos;
        GameSession session = new GameSession(new GameSetup("sub-hull-integration", map,
                List.of(new FleetSetup("light", List.of(new ShipSetup("shooter", "light", new Vector2(-200, -200),
                        0, "captain", 2, 0, 1000, "torpedo-boat", 0))),
                        new FleetSetup("dark", List.of(new ShipSetup("sub", "dark", new Vector2(0, 0),
                                heading, "diver", 2, 0, 1000, "submarine", 0)))), List.of()));
        session.updatePlayerState(new PlayerStateUpdate("diver", "dark", 0, 0, heading, 0, 0, 2, 0, 0,
                false, "submarine", DEPTH, 0, null, null, null, null, "periscope"), navigation, map);
        assertEquals(DEPTH, session.snapshot().ships().stream().filter(s -> s.id().equals("sub")).findFirst().orElseThrow().y(), .001);
        double targetY = DEPTH + aim.y() * SCALE;
        FlakFireRequest request = new FlakFireRequest("captain", "light", "shooter", startX, 3, startZ,
                -side * 300 * cos, (targetY - 3) * 300 / distance, side * 300 * sin);
        if (cannon) session.applyFireCannon(request, "gunner");
        else session.applyFireFlak(request, "gunner");
        assertEquals(1, session.snapshot().flakProjectiles().size());
        RadarService radar = new RadarService();
        for (int i = 0; i < 120; i++) session.update(.01, radar, navigation, map);
        var hits = session.snapshot().flakHits();
        assertEquals(aim.hit() ? 1 : 0, hits.size(), aim.name());
        if (aim.hit()) {
            assertEquals("sub", hits.get(0).targetShipId());
            var impact = session.snapshot().flakImpacts().stream()
                    .filter(value -> "ship-critical-hit".equals(value.reason())).findFirst().orElseThrow();
            double localZ = (impact.x() * sin + impact.z() * cos) / SCALE;
            assertEquals(aim.z(), localZ, .001, "Impact must stay in the targeted hull cross-section");
            if (aim.y() < 1.5) assertTrue(impact.y() < -1, "Not a periscope hit");
        }
    }
}
