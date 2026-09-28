package one.xis.seabattle.game;

import java.util.List;

/** Retained release/impact event; independent of projectile lifetime. */
public record DepthChargeSnapshot(String id, String shipId, String playerId, int lane,
        double releasedAt, double explodesAt, double x, double z, double heading,
        double radius, boolean exploded, List<String> targetShipIds) {}
