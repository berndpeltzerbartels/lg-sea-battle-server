package one.xis.seabattle;

public record TorpedoSnapshot(
        String id,
        String teamId,
        String shipId,
        double x,
        double z,
        double heading,
        double speed,
        String state,
        double firedAt
) {
}
