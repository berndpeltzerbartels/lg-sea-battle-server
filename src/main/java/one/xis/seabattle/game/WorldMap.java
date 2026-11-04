package one.xis.seabattle.game;

import java.util.List;

public record WorldMap(int version, List<Landmass> landmasses, InstrumentMap instrumentMap) {
  public WorldMap(int version, List<Landmass> landmasses) {
    this(version, List.copyOf(landmasses), null);
  }
}
