package one.xis.seabattle.game;

// Birth state and motion parameters are immutable and independent of the reusable fleet slot.
public record ShipWreckSnapshot(String id, double sunkAt, ShipSnapshot ship,
                                double sinkDepth, double sinkSeconds, int sinkSide) {
    public ShipWreckSnapshot(String id, double sunkAt, ShipSnapshot ship) {
        this(id,sunkAt,ship,WreckPhysics.SINK_DEPTH,WreckPhysics.DURATION,
                ship.id().hashCode()%2==0?1:-1);
    }
}
