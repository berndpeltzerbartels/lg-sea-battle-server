package one.xis.seabattle.webapp.gameconfig;

import one.xis.validation.LabelKey;
import one.xis.UseFormatter;
import one.xis.seabattle.webapp.validation.Range;
import one.xis.seabattle.webapp.validation.WholeNumberFormatter;
import one.xis.validation.Mandatory;
import java.util.List;
import java.util.Map;

/** Typed admin form, independent of the individual database rows. */
@ValidGameConfig
record GameConfigForm(
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.aimResponse") Double aimResponse,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.aimTurnDamping") Double aimTurnDamping,
        @Mandatory @Range(min = 0, max = 180) @LabelKey("gameConfig.aimMinCorrectionAngle") Double aimMinCorrectionAngle,
        @Mandatory @Range(min = 0, minInclusive = false, max = 180) @LabelKey("gameConfig.fireArc") Double fireArc,
        @Mandatory @Range(min = 0, max = 180) @LabelKey("gameConfig.closeFireArc") Double closeFireArc,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.fireMinRange") Double fireMinRange,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.fireMaxRange") Double fireMaxRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.closeFireRange") Double closeFireRange,
        @Mandatory @Range(min = 0, minInclusive = false) @LabelKey("gameConfig.torpedoCooldownMin") Double torpedoCooldownMin,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.torpedoCooldownVariation") Double torpedoCooldownVariation,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.approachSlowRange") Double approachSlowRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.closeManeuverRange") Double closeManeuverRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.radarInterceptRange") Double radarInterceptRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.ramRange") Double ramRange,
        @Mandatory @Range(min = 0) @LabelKey("gameConfig.humanTargetPriorityClearance") Double humanTargetPriorityClearance,
        @Mandatory @Range(min = 1) @UseFormatter(WholeNumberFormatter.class) @LabelKey("gameConfig.targetPreferredAttackers") Integer targetPreferredAttackers,
        @Mandatory @Range(min = 1) @LabelKey("gameConfig.targetAlternativeDistanceFactor") Double targetAlternativeDistanceFactor
) {
    /** Loading is conversion only: out-of-range values must remain editable. */
    static GameConfigForm fromParameters(List<GameConfigParameter> parameters) {
        Map<String, String> values = new java.util.HashMap<>();
        for (GameConfigParameter parameter : parameters)
            if (parameter.group() == GameConfigParameterGroup.TORPEDO_BOAT_BOT)
                values.putIfAbsent(parameter.name(), parameter.value());
        return new GameConfigForm(
                number(values.get("AIM_RESPONSE")),
                number(values.get("AIM_TURN_DAMPING")),
                degrees(number(values.get("AIM_MIN_CORRECTION_ANGLE"))),
                degrees(number(values.get("FIRE_ARC"))),
                degrees(number(values.get("CLOSE_FIRE_ARC"))),
                meters(number(values.get("FIRE_MIN_RANGE"))),
                meters(number(values.get("FIRE_MAX_RANGE"))),
                meters(number(values.get("CLOSE_FIRE_RANGE"))),
                number(values.get("TORPEDO_COOLDOWN_MIN")),
                number(values.get("TORPEDO_COOLDOWN_VARIATION")),
                meters(number(values.get("APPROACH_SLOW_RANGE"))),
                meters(number(values.get("CLOSE_MANEUVER_RANGE"))),
                meters(number(values.get("RADAR_INTERCEPT_RANGE"))),
                meters(number(values.get("RAM_RANGE"))),
                meters(number(values.get("HUMAN_TARGET_PRIORITY_CLEARANCE"))),
                integer(values.get("TARGET_PREFERRED_ATTACKERS")),
                number(values.get("TARGET_ALTERNATIVE_DISTANCE_FACTOR")));
    }

    // Same world scale as the existing game display: 20 metres per world unit.
    private static final double METERS_PER_WORLD_UNIT = 20;

    private static Double meters(Double value) { return value == null ? null : finite(value * METERS_PER_WORLD_UNIT); }
    private static Double degrees(Double value) { return value == null ? null : finite(Math.toDegrees(value)); }

    private static Double finite(double value) { return Double.isFinite(value) ? value : null; }

    private static Double number(String value) {
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) ? number : null;
        } catch (NullPointerException | NumberFormatException error) {
            return null; // Unrepresentable database content leaves an editable, required field.
        }
    }

    private static Integer integer(String value) {
        try {
            return new java.math.BigDecimal(value).intValueExact();
        } catch (NullPointerException | NumberFormatException | ArithmeticException error) {
            return null;
        }
    }

    boolean complete() {
        return aimResponse != null
                && aimTurnDamping != null
                && aimMinCorrectionAngle != null
                && fireArc != null
                && closeFireArc != null
                && fireMinRange != null
                && fireMaxRange != null
                && closeFireRange != null
                && torpedoCooldownMin != null
                && torpedoCooldownVariation != null
                && approachSlowRange != null
                && closeManeuverRange != null
                && radarInterceptRange != null
                && ramRange != null
                && humanTargetPriorityClearance != null
                && targetPreferredAttackers != null
                && targetAlternativeDistanceFactor != null;
    }

    List<GameConfigParameter> applyTo(List<GameConfigParameter> parameters) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("AIM_RESPONSE", aimResponse.toString()),
                Map.entry("AIM_TURN_DAMPING", aimTurnDamping.toString()),
                Map.entry("AIM_MIN_CORRECTION_ANGLE", Double.toString(Math.toRadians(aimMinCorrectionAngle))),
                Map.entry("FIRE_ARC", Double.toString(Math.toRadians(fireArc))),
                Map.entry("CLOSE_FIRE_ARC", Double.toString(Math.toRadians(closeFireArc))),
                Map.entry("FIRE_MIN_RANGE", Double.toString(fireMinRange / METERS_PER_WORLD_UNIT)),
                Map.entry("FIRE_MAX_RANGE", Double.toString(fireMaxRange / METERS_PER_WORLD_UNIT)),
                Map.entry("CLOSE_FIRE_RANGE", Double.toString(closeFireRange / METERS_PER_WORLD_UNIT)),
                Map.entry("TORPEDO_COOLDOWN_MIN", torpedoCooldownMin.toString()),
                Map.entry("TORPEDO_COOLDOWN_VARIATION", torpedoCooldownVariation.toString()),
                Map.entry("APPROACH_SLOW_RANGE", Double.toString(approachSlowRange / METERS_PER_WORLD_UNIT)),
                Map.entry("CLOSE_MANEUVER_RANGE", Double.toString(closeManeuverRange / METERS_PER_WORLD_UNIT)),
                Map.entry("RADAR_INTERCEPT_RANGE", Double.toString(radarInterceptRange / METERS_PER_WORLD_UNIT)),
                Map.entry("RAM_RANGE", Double.toString(ramRange / METERS_PER_WORLD_UNIT)),
                Map.entry("HUMAN_TARGET_PRIORITY_CLEARANCE", Double.toString(humanTargetPriorityClearance / METERS_PER_WORLD_UNIT)),
                Map.entry("TARGET_PREFERRED_ATTACKERS", targetPreferredAttackers.toString()),
                Map.entry("TARGET_ALTERNATIVE_DISTANCE_FACTOR", targetAlternativeDistanceFactor.toString()));
        return parameters.stream().map(p -> p.group() != GameConfigParameterGroup.TORPEDO_BOAT_BOT ? p
                : new GameConfigParameter(p.id(), p.group(), p.type(), p.name(), p.title(), p.description(),
                        values.getOrDefault(p.name(), p.value()))).toList();
    }
}
