package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandGeometryTest {
    @Test
    void broadPhaseMatchesOriginalCollisionTestAcrossBothWorlds() {
        WorldMapService maps = new WorldMapService();
        Random random = new Random(20260928);
        for (WorldMap world : List.of(maps.world(), maps.denseWorld())) {
            for (Landmass land : world.landmasses()) {
                for (int sample = 0; sample < 500; sample++) {
                    Vector2 point = new Vector2(land.x() + (random.nextDouble() * 8 - 4) * land.rx(),
                            land.z() + (random.nextDouble() * 8 - 4) * land.rz());
                    assertEquals(original(point, land), LandGeometry.isBlockedByLandmass(point, land));
                }
                checkBoundary(land);
            }
            for (int sample = 0; sample < 3000; sample++) {
                Vector2 point = new Vector2(random.nextDouble() * 6000 - 3000,
                        random.nextDouble() * 6000 - 3000);
                boolean expected = world.landmasses().stream().anyMatch(land -> original(point, land));
                assertEquals(expected, LandGeometry.isBlocked(point, world));
            }
        }
    }

    @Test
    void widestCoastFjordsLakesAndWaterwaysKeepTheirExactBoundaries() {
        Landmass coast = new Landmass("coastline", "test_coast", 100, -100,
                90, 30, 90, 30, 90, 30, null, 1, null, 10.0, null,
                List.of(new Fjord(1.2, 0.3, 0.9)),
                List.of(new Waterway(new Point2(-150, 0), new Point2(150, 0), 4)),
                List.of(new Lake(0, 12, 4, 3)));
        checkBoundary(coast);
        assertFalse(LandGeometry.isBlockedByLandmass(new Vector2(100, -100), coast));
        assertFalse(LandGeometry.isBlockedByLandmass(new Vector2(100, -88), coast));
        assertTrue(LandGeometry.isBlockedByLandmass(new Vector2(100, -110), coast));
        // This exaggerated roughness reaches the maximum radius, beyond the nominal ellipse.
        boolean foundExtendedCoast = false;
        for (int angle = 0; angle < 360; angle++) {
            double radians = Math.toRadians(angle);
            Vector2 point = pointOnRing(coast, radians, 1.4);
            if (original(point, coast)) {
                foundExtendedCoast = true;
                assertTrue(LandGeometry.isBlockedByLandmass(point, coast));
            }
        }
        assertTrue(foundExtendedCoast);
    }

    private void checkBoundary(Landmass land) {
        for (int angle = 0; angle < 360; angle += 3) {
            double radians = Math.toRadians(angle);
            double boundary = LandGeometry.navigationBlockDistance(land)
                    / LandGeometry.shapeDistance(pointOnRing(land, radians, 1), land);
            for (double ring : new double[]{Math.nextDown(boundary), boundary, Math.nextUp(boundary),
                    boundary - 1e-8, boundary + 1e-8}) {
                Vector2 point = pointOnRing(land, radians, ring);
                assertEquals(original(point, land), LandGeometry.isBlockedByLandmass(point, land),
                        () -> land.name() + " at " + point);
            }
        }
    }

    private Vector2 pointOnRing(Landmass land, double angle, double ring) {
        return new Vector2(land.x() + Math.cos(angle) * land.rx() * ring,
                land.z() + Math.sin(angle) * land.rz() * ring);
    }

    private boolean original(Vector2 point, Landmass land) {
        return LandGeometry.shapeDistance(point, land) < LandGeometry.navigationBlockDistance(land)
                && !LandGeometry.isInLandWater(point, land);
    }
}
