package one.xis.seabattle;

import java.util.List;

public record RadarSnapshot(
        String type,
        String sessionId,
        double t,
        String shipId,
        String teamId,
        double observerX,
        double observerZ,
        double observerHeading,
        double range,
        List<RadarContact> contacts
) {
}
