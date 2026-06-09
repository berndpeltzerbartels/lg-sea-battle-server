package one.xis.seabattle;

public record TorpedoImpactSnapshot(
        String id,
        String teamId,
        String shipId,
        String targetShipId,
        String reason,
        double x,
        double z,
        double heading,
        double t
) {
}
