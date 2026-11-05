package one.xis.seabattle.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpenIslandsRockFieldTest {
    @Test void theMarkedRockFieldIsOpenWater() {
        var world = new WorldMapService().world();
        assertFalse(world.landmasses().stream().anyMatch(land -> land.name().startsWith("middle_skerry_")
                || land.name().startsWith("l_passage_")));
        for (var point : List.of(new Vector2(486,-548), new Vector2(536,-502),new Vector2(592,-555),
                new Vector2(690,-560),new Vector2(760,-625),new Vector2(820,-500),new Vector2(735,-430),
                new Vector2(690,-710),new Vector2(840,-660),new Vector2(920,-560),new Vector2(608,-390),new Vector2(832,-760)))
            assertFalse(LandGeometry.isBlocked(point,world),point.toString());
        assertTrue(world.landmasses().stream().anyMatch(land -> land.name().equals("mist_gate_north")));
        assertTrue(world.landmasses().stream().anyMatch(land -> land.name().equals("southern_cliffs")));
    }

    @Test void theDenseLandscapeRetainsItsExistingPassageIslands() {
        var world = new WorldMapService().denseWorld();
        assertTrue(world.landmasses().stream().anyMatch(land -> land.name().equals("l_passage_west_arm")));
        assertTrue(world.landmasses().stream().anyMatch(land -> land.name().equals("l_passage_north_arm")));
        assertEquals(16,world.version());
    }
}
