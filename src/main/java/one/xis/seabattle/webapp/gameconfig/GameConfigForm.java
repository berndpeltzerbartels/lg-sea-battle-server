package one.xis.seabattle.webapp.gameconfig;

import one.xis.validation.LabelKey;
import one.xis.UseFormatter;
import one.xis.seabattle.webapp.validation.Range;
import one.xis.seabattle.webapp.validation.WholeNumberFormatter;
import one.xis.validation.Mandatory;
import java.util.List;
import java.util.Map;

/** Typed admin form, independent of the individual database rows. */
record GameConfigForm(
        Double aimResponse,
        Double aimTurnDamping,
        Double aimMinCorrectionAngle,
        Double fireArc,
        Double closeFireArc,
        Double fireMinRange,
        Double fireMaxRange,
        Double closeFireRange,
        Double torpedoCooldownMin,
        Double torpedoCooldownVariation,
        Double approachSlowRange,
        Double closeManeuverRange,
        Double radarInterceptRange,
        Double ramRange,
        Double humanTargetPriorityClearance,
        Integer targetPreferredAttackers,
        Double targetAlternativeDistanceFactor,
        Double aimErrorAngle
) {
    /** Loading is conversion only: out-of-range values must remain editable. */
    static GameConfigForm fromParameters(List<GameConfigParameter> parameters) {
        return fromParameters(parameters, GameConfigParameterGroup.TORPEDO_BOAT_BOT);
    }

    static GameConfigForm fromParameters(List<GameConfigParameter> parameters, GameConfigParameterGroup group) {
        Map<String, String> values = new java.util.HashMap<>();
        for (GameConfigParameter parameter : parameters)
            if (parameter.group() == group)
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
                number(values.get("TARGET_ALTERNATIVE_DISTANCE_FACTOR")),
                degrees(number(values.get("AIM_ERROR_ANGLE"))));
    }

    // Same world scale as the existing game display: 20 metres per world unit.
    private static final double METERS_PER_WORLD_UNIT = 20;

    static Double meters(Double value) { return value == null ? null : finite(value * METERS_PER_WORLD_UNIT); }
    static Double degrees(Double value) { return value == null ? null : finite(Math.toDegrees(value)); }

    private static Double finite(double value) { return Double.isFinite(value) ? value : null; }

    static Double number(String value) {
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
                && targetAlternativeDistanceFactor != null
                && aimErrorAngle != null;
    }

    List<GameConfigParameter> applyTo(List<GameConfigParameter> parameters) {
        return applyTo(parameters, GameConfigParameterGroup.TORPEDO_BOAT_BOT);
    }

    List<GameConfigParameter> applyTo(List<GameConfigParameter> parameters, GameConfigParameterGroup group) {
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
                Map.entry("TARGET_ALTERNATIVE_DISTANCE_FACTOR", targetAlternativeDistanceFactor.toString()),
                Map.entry("AIM_ERROR_ANGLE", Double.toString(Math.toRadians(aimErrorAngle))));
        return parameters.stream().map(p -> p.group() != group ? p
                : new GameConfigParameter(p.id(), p.group(), p.type(), p.name(), p.title(), p.description(),
                        values.getOrDefault(p.name(), p.value()))).toList();
    }
}
