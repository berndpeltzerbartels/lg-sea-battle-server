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
    @Test void spawnSubsetStaysInsideTheEstablishedBattleAreaWhileTheFullGridRemainsReusable() {
        var setup=new DefaultGameSetupFactory(new WorldMapService()).defaultSetup();
        var grid=WaterPointGrid.scan(setup.worldMap(),setup.respawnCandidates());
        var continent=setup.worldMap().landmasses().stream().filter(l -> l.name().equals("western_continent")).findFirst().orElseThrow();
        java.util.function.Predicate<Vector2> behind=p -> p.x()<continent.x()-continent.rx()
                && Math.abs(p.z()-continent.z())<continent.rz();
        assertTrue(grid.points().stream().anyMatch(behind),"Full water grid must still contain the far side");
        assertTrue(grid.spawnPoints().size()>100);
        assertTrue(grid.spawnPoints().size()<grid.points().size());
        assertFalse(grid.spawnPoints().stream().anyMatch(behind));
        assertTrue(grid.points().containsAll(grid.spawnPoints()));
        assertSame(grid.spawnPoints(),grid.spawnPoints(),"Selection is prepared once, not recomputed on respawn");
        assertThrows(UnsupportedOperationException.class,()->grid.spawnPoints().clear());
    }
    @Test void undefinedPlayAreaDoesNotSilentlyUseAllWaterPointsForSpawning() {
        var grid=WaterPointGrid.scan(new WorldMap(1,List.of()),List.of());
        assertFalse(grid.points().isEmpty());
        assertTrue(grid.spawnPoints().isEmpty());
    }

}
