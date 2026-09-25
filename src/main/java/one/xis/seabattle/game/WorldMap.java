package one.xis.seabattle.game;

import java.util.List;

record WorldMap(int version, List<Landmass> landmasses, InstrumentMap instrumentMap) {
    WorldMap(int version, List<Landmass> landmasses) {
        this(version, List.copyOf(landmasses), null);
    }
}
