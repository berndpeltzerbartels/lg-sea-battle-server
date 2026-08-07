package one.xis.seabattle;

public record ClientPlaneHitRequest(
        String playerId,
        String teamId,
        String shipId,
        String targetShipId,
        String weaponType,
        double x,
        double y,
        double z
) {
}
