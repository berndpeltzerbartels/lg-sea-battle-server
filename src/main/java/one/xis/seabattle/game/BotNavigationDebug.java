package one.xis.seabattle.game;

import java.util.List;

/** Created only when the debug map requests it; ordinary game snapshots stay unchanged. */
public record BotNavigationDebug(String instanceId, double t, List<Plan> plans) {
    public record Plan(String shipId, String kind, String targetId, Vector2 destination,
                       Vector2 largeWaypoint, Vector2 routeWaypoint) { }
}
