package one.xis.seabattle.game;

import java.util.List;

record WorldMap(int version, List<Landmass> landmasses, List<MapObject> mapObjects) {
    WorldMap(int version, List<Landmass> landmasses) {
        this(version, landmasses, List.of());
    }

    WorldMap {
        landmasses = landmasses == null ? List.of() : List.copyOf(landmasses);
        mapObjects = mapObjects == null ? List.of() : List.copyOf(mapObjects);
    }
}
