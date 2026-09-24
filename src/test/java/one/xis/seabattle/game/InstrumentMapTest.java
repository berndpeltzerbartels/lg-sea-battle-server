package one.xis.seabattle.game;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.awt.geom.Path2D;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentMapTest {
    @Test
    void builtInWorldAlsoProducesAllInstrumentLayers() {
        var map = InstrumentMap.prepare(new WorldMapService().world());
        assertEquals(3, map.layers().size());
        assertFalse(map.layers().get(0).contours().isEmpty());
    }

    @Test
    void contoursMatchPeakHeightProfilesAndSurviveSerialization() {
        for (String falloff : new String[]{"hill", "spike", "plateau"}) {
            var root = JsonParser.parseString("""
                    {"islands":[{"id":"peak","seaFloorHeight":-80,
                    "polygon":[{"x":-100,"z":-100},{"x":100,"z":-100},{"x":100,"z":100},{"x":-100,"z":100}],
                    "heights":[{"x":20,"z":10,"h":200,"falloff":"%s"}]}]}
                    """.formatted(falloff)).getAsJsonObject();
            var world = new LandscapeModelConverter().convertEditorLandscape(root);
            var prepared = InstrumentMap.prepare(world);
            assertEquals(prepared, new Gson().fromJson(new Gson().toJson(prepared), InstrumentMap.class));
            for (var layer : prepared.layers()) {
                Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
                for (var ring : layer.contours()) {
                    path.moveTo(ring.get(0).x(), ring.get(0).z());
                    ring.forEach(p -> path.lineTo(p.x(), p.z())); path.closePath();
                }
                for (int x = -95; x < 100; x += 13) for (int z = -95; z < 100; z += 17) {
                    assertEquals(LandGeometry.isBlockedAtOrAbove(new Vector2(x, z), world, layer.height()), path.contains(x, z), falloff + " " + layer.height());
                }
            }
        }
    }
}
