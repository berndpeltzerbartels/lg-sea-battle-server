package one.xis.seabattle.webapp.gameconfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Conversion only; field and range validation live in GameSettingsForm. */
record SubmarineTacticsForm(
        Double diveRange,
        Double deepRange,
        Double safeRange,
        Double shotAscentRange,
        Double surfaceRange,
        Double attackMinRange,
        Double attackMaxRange,
        Double aimTolerance,
        Double aircraftMemorySeconds,
        Double underwaterContactRange) {
    static SubmarineTacticsForm fromParameters(List<GameConfigParameter> parameters) {
        var values = parameters.stream().filter(p -> p.group() == GameConfigParameterGroup.SUBMARINE_BOT)
                .collect(Collectors.toMap(GameConfigParameter::name, GameConfigParameter::value));
        return new SubmarineTacticsForm(
                GameConfigForm.meters(GameConfigForm.number(values.get("DIVE_RANGE"))),
                GameConfigForm.meters(GameConfigForm.number(values.get("DEEP_RANGE"))),
                GameConfigForm.meters(GameConfigForm.number(values.get("SAFE_RANGE"))),
                GameConfigForm.meters(GameConfigForm.number(values.get("SHOT_ASCENT_RANGE"))),
                GameConfigForm.meters(GameConfigForm.number(values.get("SURFACE_RANGE"))),
                GameConfigForm.meters(GameConfigForm.number(values.get("ATTACK_MIN_RANGE"))),
                GameConfigForm.meters(GameConfigForm.number(values.get("ATTACK_MAX_RANGE"))),
                GameConfigForm.degrees(GameConfigForm.number(values.get("AIM_TOLERANCE"))),
                GameConfigForm.number(values.get("AIRCRAFT_MEMORY_SECONDS")),
                GameConfigForm.meters(GameConfigForm.number(values.get("UNDERWATER_CONTACT_RANGE"))));
    }
    boolean complete() { return diveRange != null && deepRange != null && safeRange != null && shotAscentRange != null && surfaceRange != null && attackMinRange != null && attackMaxRange != null && aimTolerance != null && aircraftMemorySeconds != null && underwaterContactRange != null; }
    List<GameConfigParameter> applyTo(List<GameConfigParameter> parameters) {
        Map<String,String> values = Map.ofEntries(
                Map.entry("DIVE_RANGE", Double.toString(diveRange / 20)),
                Map.entry("DEEP_RANGE", Double.toString(deepRange / 20)),
                Map.entry("SAFE_RANGE", Double.toString(safeRange / 20)),
                Map.entry("SHOT_ASCENT_RANGE", Double.toString(shotAscentRange / 20)),
                Map.entry("SURFACE_RANGE", Double.toString(surfaceRange / 20)),
                Map.entry("ATTACK_MIN_RANGE", Double.toString(attackMinRange / 20)),
                Map.entry("ATTACK_MAX_RANGE", Double.toString(attackMaxRange / 20)),
                Map.entry("AIM_TOLERANCE", Double.toString(Math.toRadians(aimTolerance))),
                Map.entry("AIRCRAFT_MEMORY_SECONDS", aircraftMemorySeconds.toString()),
                Map.entry("UNDERWATER_CONTACT_RANGE", Double.toString(underwaterContactRange / 20)));
        return parameters.stream().map(p -> p.group() == GameConfigParameterGroup.SUBMARINE_BOT && values.containsKey(p.name())
                ? new GameConfigParameter(p.id(), p.group(), p.type(), p.name(), p.title(), p.description(), values.get(p.name())) : p).toList();
    }
}
