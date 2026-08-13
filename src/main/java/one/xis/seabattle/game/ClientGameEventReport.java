package one.xis.seabattle.game;

public record ClientGameEventReport(
        String playerId,
        String teamId,
        String shipId,
        String event,
        double clientTime,
        double x,
        double z,
        double heading,
        double speed,
        String damageState,
        int localTorpedoes,
        int serverTorpedoes,
        int serverTorpedoVisuals,
        String details
) {
}
