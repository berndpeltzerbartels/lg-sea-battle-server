package one.xis.seabattle.game;

/** Detached vehicle at destruction; the fleet's later respawn cannot change this wreck. */
record Wreck(
    String id, double sunkAt, Vehicle ship, double sinkDepth, double sinkSeconds, int sinkSide) {
  Wreck(String id, double sunkAt, Vehicle ship) {
    this(
        id,
        sunkAt,
        ship,
        WreckPhysics.SINK_DEPTH,
        WreckPhysics.DURATION,
        ship.id().hashCode() % 2 == 0 ? 1 : -1);
  }

  ShipWreckSnapshot snapshot() {
    return new ShipWreckSnapshot(id, sunkAt, ship.snapshot(), sinkDepth, sinkSeconds, sinkSide);
  }
}
