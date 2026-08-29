package one.xis.seabattle.game;

public record ProjectileHitSnapshot(
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
