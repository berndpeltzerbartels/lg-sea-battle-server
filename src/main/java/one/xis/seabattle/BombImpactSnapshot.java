package one.xis.seabattle;

public record BombImpactSnapshot(
        String id,
        String teamId,
        String shipId,
        String targetShipId,
        String reason,
        double x,
        double z,
        double t
) {
}
