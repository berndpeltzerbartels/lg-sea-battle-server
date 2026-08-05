package one.xis.seabattle;

public record RamHitSnapshot(
        String id,
        String teamId,
        String shipId,
        String targetShipId,
        double x,
        double z,
        double t
) {
}
