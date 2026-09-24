package one.xis.seabattle.game;

import com.google.gson.JsonParser;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MappedPlateauGeometryTest {
    @Test
    void externalMappedPerformanceFixturePassesProductionConverter() throws Exception {
        String path = System.getenv("MAPPED_LANDSCAPE_TEST_FILE");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null);
        var root = JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(path))).getAsJsonObject();
        var map = new LandscapeModelConverter().convertEditorLandscape(root);
        assertEquals(100, map.landmasses().size());
        for (var land : map.landmasses()) {
            assertTrue(MappedPlateauGeometry.complete(land), land.name());
            assertTrue(Double.isFinite(MappedPlateauGeometry.height(new Vector2(land.x(), land.z()), land)));
        }
        var prepared = InstrumentMap.prepare(map);
        assertEquals(3, prepared.layers().size());
        assertFalse(prepared.layers().get(0).contours().isEmpty());
        String output = System.getenv("MAPPED_LANDSCAPE_TEST_OUTPUT");
        if (output != null) java.nio.file.Files.writeString(java.nio.file.Path.of(output), new Gson().toJson(map.withInstrumentMap(prepared)));
    }

    @Test
    void incompleteAndInvalidMappingsAreRejectedDuringImport() {
        var root = JsonParser.parseString("""
                {"islands":[{"name":"Testbank","polygon":[
                {"x":-100,"z":-100,"boundaryPointId":"a"},
                {"x":100,"z":-100,"boundaryPointId":"b"},
                {"x":0,"z":100,"boundaryPointId":"c"}],
                "heights":[
                {"x":-10,"z":-10,"h":2,"plateauGroupId":"p","plateauOrder":0,"plateauBoundaryPointId":"a"},
                {"x":10,"z":-10,"h":2,"plateauGroupId":"p","plateauOrder":1,"plateauBoundaryPointId":"b"},
                {"x":0,"z":10,"h":2,"plateauGroupId":"p","plateauOrder":2,"plateauBoundaryPointId":"c"}]}]}
                """).getAsJsonObject();
        var converter = new LandscapeModelConverter();
        assertDoesNotThrow(() -> converter.convertEditorLandscape(root));
        var heights = root.getAsJsonArray("islands").get(0).getAsJsonObject().getAsJsonArray("heights");
        var point = heights.get(0).getAsJsonObject();
        point.remove("plateauBoundaryPointId");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> converter.convertEditorLandscape(root)).getMessage().contains("Randzuordnung"));
        for (String invalid : new String[]{"", "unknown", "b"}) {
            point.addProperty("plateauBoundaryPointId", invalid);
            assertThrows(IllegalArgumentException.class, () -> converter.convertEditorLandscape(root));
        }
        point.addProperty("plateauBoundaryPointId", "a");
        heights.remove(2);
        assertThrows(IllegalArgumentException.class, () -> converter.convertEditorLandscape(root));
    }

    @Test
    void mappingsSurviveConversionAndSerializationAndDriveServerHeights() {
        var root = JsonParser.parseString("""
                {"islands":[{"id":"bank","material":"sand","seaFloorHeight":-80,
                "polygon":[{"x":-200,"z":-200,"boundaryPointId":"a"},
                {"x":200,"z":-200,"boundaryPointId":"b"},{"x":200,"z":200,"boundaryPointId":"c"},
                {"x":-200,"z":200,"boundaryPointId":"d"}],
                "heights":[{"x":-100,"z":-40,"h":2,"plateauGroupId":"p","plateauOrder":0,"plateauBoundaryPointId":"a"},
                {"x":-40,"z":-40,"h":2,"plateauGroupId":"p","plateauOrder":1,"plateauBoundaryPointId":"b"},
                {"x":-40,"z":40,"h":2,"plateauGroupId":"p","plateauOrder":2,"plateauBoundaryPointId":"c"},
                {"x":-100,"z":40,"h":2,"plateauGroupId":"p","plateauOrder":3,"plateauBoundaryPointId":"d"}]}]}
                """).getAsJsonObject();
        var map = new LandscapeModelConverter().convertEditorLandscape(root);
        var gson = new Gson();
        var restored = gson.fromJson(gson.toJson(map), WorldMap.class);
        var land = restored.landmasses().get(0);
        assertEquals("a", land.polygon().get(0).boundaryPointId());
        assertEquals("a", land.heightPoints().get(0).plateauBoundaryPointId());
        assertTrue(MappedPlateauGeometry.complete(land));
        assertEquals(2, MappedPlateauGeometry.height(new Vector2(-70, 0), land), 1e-8);
        assertEquals(-39, MappedPlateauGeometry.height(new Vector2(-70, -120), land), 1e-8);
        assertEquals(-80, MappedPlateauGeometry.height(new Vector2(-200, -200), land), 1e-8);
        assertTrue(LandGeometry.isBlockedByLandmass(new Vector2(-70, 0), land));
        assertFalse(LandGeometry.isBlockedByLandmass(new Vector2(-70, -120), land));
        var heights = root.getAsJsonArray("islands").get(0).getAsJsonObject().getAsJsonArray("heights");
        for (int i = 0; i < 4; i++) {
            var point = heights.get(i).getAsJsonObject().deepCopy();
            point.addProperty("x", point.get("x").getAsDouble() + 140);
            point.addProperty("plateauGroupId", "second");
            heights.add(point);
        }
        var two = new LandscapeModelConverter().convertEditorLandscape(root).landmasses().get(0);
        assertEquals(2, MappedPlateauGeometry.height(new Vector2(70, 0), two), 1e-8);
        assertEquals(-11.6666666667, MappedPlateauGeometry.height(new Vector2(0, 0), two), 1e-7);
        for (int x = -190; x < 200; x += 17) for (int z = -190; z < 200; z += 19) {
            var p = new Vector2(x, z);
            assertTrue(MappedPlateauGeometry.height(p, two) >= MappedPlateauGeometry.height(p, land) - 1e-8);
        }
    }
}
