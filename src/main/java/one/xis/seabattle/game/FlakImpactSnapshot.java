package one.xis.seabattle.game;

public record FlakImpactSnapshot(
        String id,
        String teamId,
        String shipId,
        String reason,
        double x,
        double y,
        double z,
        double t
) {
}
