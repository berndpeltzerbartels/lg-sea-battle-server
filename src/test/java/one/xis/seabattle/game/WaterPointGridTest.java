package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WaterPointGridTest {
    @Test void staticPointsAvoidLandAndProvideHullClearance() {
        WorldMap world = new WorldMapService().denseWorld();
        var grid = WaterPointGrid.scan(world,List.of());
        assertTrue(grid.points().size() > 100);
        for (Vector2 point : grid.points()) assertTrue(WaterPointGrid.hasClearance(point,world));
        assertThrows(UnsupportedOperationException.class, () -> grid.points().clear());
    }
    @Test void identicalLandscapeRequestsReuseTheScannedGrid() {
        var world = new WorldMap(987,List.of());
        var grid = WaterPointGrid.cached(world,List.of());
        assertSame(grid,WaterPointGrid.cached(new WorldMap(988,List.of()),List.of()));
    }
}
