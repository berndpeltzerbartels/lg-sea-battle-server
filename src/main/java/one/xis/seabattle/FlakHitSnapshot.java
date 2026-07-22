package one.xis.seabattle;

public record FlakHitSnapshot(
        String id,
        String teamId,
        String shipId,
        String targetShipId,
        double x,
        double y,
        double z,
        double t
) {
}
