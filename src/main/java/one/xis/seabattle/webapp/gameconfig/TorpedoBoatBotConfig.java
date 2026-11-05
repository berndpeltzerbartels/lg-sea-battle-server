package one.xis.seabattle.webapp.gameconfig;

import java.util.List;

import static one.xis.seabattle.webapp.gameconfig.GameConfigParameterType.*;

/** Typed runtime values. Distances are world units, angles radians and time values seconds. */
public record TorpedoBoatBotConfig(
        double aimResponse,
        double aimTurnDamping,
        double aimMinCorrectionAngle,
        double fireArc,
        double closeFireArc,
        double fireMinRange,
        double fireMaxRange,
        double closeFireRange,
        double torpedoCooldownMin,
        double torpedoCooldownVariation,
        double approachSlowRange,
        double closeManeuverRange,
        double radarInterceptRange,
        double ramRange,
        double humanTargetPriorityClearance,
        int targetPreferredAttackers,
        double targetAlternativeDistanceFactor
) {
    /** Available immediately, before the scheduler's first database load. */
    public static TorpedoBoatBotConfig defaults() {
        return new TorpedoBoatBotConfig(.40, .45, .0015, .16, 1.15, 195, 690, 435,
                10.5, 3, 230, 130, 360, 102, 320, 2, 1.3);
    }

    static TorpedoBoatBotConfig fromParameters(List<GameConfigParameter> parameters) {
        var values = new GameConfigValues(parameters, GameConfigParameterGroup.TORPEDO_BOAT_BOT);
        return new TorpedoBoatBotConfig(
                values.number("AIM_RESPONSE", NUMBER),
                values.number("AIM_TURN_DAMPING", NUMBER),
                values.number("AIM_MIN_CORRECTION_ANGLE", ANGLE),
                values.number("FIRE_ARC", ANGLE),
                values.number("CLOSE_FIRE_ARC", ANGLE),
                values.number("FIRE_MIN_RANGE", DISTANCE),
                values.number("FIRE_MAX_RANGE", DISTANCE),
                values.number("CLOSE_FIRE_RANGE", DISTANCE),
                values.number("TORPEDO_COOLDOWN_MIN", SECONDS),
                values.number("TORPEDO_COOLDOWN_VARIATION", SECONDS),
                values.number("APPROACH_SLOW_RANGE", DISTANCE),
                values.number("CLOSE_MANEUVER_RANGE", DISTANCE),
                values.number("RADAR_INTERCEPT_RANGE", DISTANCE),
                values.number("RAM_RANGE", DISTANCE),
                values.number("HUMAN_TARGET_PRIORITY_CLEARANCE", DISTANCE),
                values.integer("TARGET_PREFERRED_ATTACKERS"),
                values.number("TARGET_ALTERNATIVE_DISTANCE_FACTOR", NUMBER));
    }

}
