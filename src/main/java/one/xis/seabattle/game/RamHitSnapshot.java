package one.xis.seabattle.game;

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
