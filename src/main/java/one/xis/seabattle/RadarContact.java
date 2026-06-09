package one.xis.seabattle;

public record RadarContact(
        String id,
        String teamId,
        double x,
        double z,
        double heading,
        double distance,
        double bearing
) {
}
