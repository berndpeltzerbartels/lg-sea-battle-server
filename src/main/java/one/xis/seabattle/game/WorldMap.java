package one.xis.seabattle.game;

import java.util.List;

record WorldMap(int version, List<Landmass> landmasses, List<MapObject> mapObjects, InstrumentMap instrumentMap) {
    WorldMap(int version, List<Landmass> landmasses, List<MapObject> mapObjects) {
        this(version, landmasses, mapObjects, null);
    }

    WorldMap withInstrumentMap(InstrumentMap prepared) {
        return new WorldMap(version, landmasses, mapObjects, prepared);
    }
    WorldMap(int version, List<Landmass> landmasses) {
        this(version, landmasses, List.of());
    }

    WorldMap {
        landmasses = landmasses == null ? List.of() : List.copyOf(landmasses);
        mapObjects = mapObjects == null ? List.of() : List.copyOf(mapObjects);
    }
}
