package one.xis.seabattle.game;

public record TorpedoSnapshot(
        String id,
        String teamId,
        String shipId,
        double x,
        double z,
        double heading,
        double speed,
        String state,
        double firedAt,
        int tubeSide
) {
}
