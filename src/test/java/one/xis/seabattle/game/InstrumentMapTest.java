package one.xis.seabattle.game;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.awt.geom.Path2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentMapTest {
    @Test
    void fractionalGridProducesOneOutlineWithoutInternalSeams() {
        for (double offset : new double[]{0, 0.123, -1783.719}) {
            Landmass land = new Landmass("coastline", "fractional", offset, offset,
                    173.37, 211.19, 173.37, 211.19, 173.37, 211.19,
                    null, 20, 200.0, 0.0, null, List.of(), List.of(), List.of());
            var data = InstrumentMap.prepare(new WorldMap(1, List.of(land)));
            assertEquals(1, data.layers().get(0).contours().size(),
                    "one continuous island must have one outline at offset " + offset);
        }
    }

    @Test
    void preparesAllLayersAndPreservesWaterHoles() {
        Landmass land = new Landmass("coastline", "test", 0, 0, 500, 500,
                500, 500, 500, 500, null, 20, 200.0, 0.0, null,
                List.of(), List.of(), List.of(new Lake(0, 0, 30, 30)));
        var data = InstrumentMap.prepare(new WorldMap(1, List.of(land)));
        assertEquals(List.of(0.0, 50.0, 150.0), data.layers().stream().map(InstrumentMap.Layer::height).toList());
        for (var layer : data.layers()) {
            var shape = shape(layer);
            assertFalse(shape.contains(0, 0), "lake stays water");
            assertFalse(shape.contains(900, 900), "open sea stays empty");
            assertTrue(shape.contains(80, 0), "high terrain present at every level");
        }
        var duplicate = InstrumentMap.prepare(new WorldMap(1, List.of(land, land)));
        assertEquals(data, duplicate, "overlapping land is united, not cancelled by even-odd filling");
    }

    @Test
    void cachesCompleteDefaultMapAndRebuildsAfterWorldChange() throws Exception {
        var service = new GameStateService(new DefaultGameSetupFactory(new WorldMapService()),
                new RadarService(), new NavigationService());
        var world = service.worldMap();
        assertSame(world, service.worldMap());
        assertEquals(3, world.instrumentMap().layers().size());
        assertEquals(73, world.instrumentMap().layers().get(0).contours().size(),
                "default world must not acquire internal strip boundaries when areas overlap");
        String output = System.getProperty("seaBattle.instrumentFixture");
        if (output != null) Files.writeString(Path.of(output), new Gson().toJson(world));
        service.resetToSetup("ram-side");
        assertNotSame(world, service.worldMap());
        assertSame(service.worldMap(), service.worldMap());
    }

    private Path2D shape(InstrumentMap.Layer layer) {
        var path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (var ring : layer.contours()) {
            path.moveTo(ring.get(0).x(), ring.get(0).z());
            for (var p : ring) path.lineTo(p.x(), p.z());
            path.closePath();
        }
        return path;
    }
}
