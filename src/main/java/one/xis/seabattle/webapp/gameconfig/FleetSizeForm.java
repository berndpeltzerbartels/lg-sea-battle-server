package one.xis.seabattle.webapp.gameconfig;

import one.xis.UseFormatter;
import one.xis.validation.Mandatory;
import one.xis.validation.LabelKey;
import one.xis.seabattle.webapp.validation.Range;
import one.xis.seabattle.webapp.validation.WholeNumberFormatter;
import java.util.List;

record FleetSizeForm(
        Integer lightTorpedoBoats,
        Integer darkTorpedoBoats,
        Integer lightSubmarines,
        Integer darkSubmarines,
        Integer lightPlanes,
        Integer darkPlanes) {
    static FleetSizeForm fromParameters(List<GameConfigParameter> parameters) {
        return new FleetSizeForm(value(parameters, "LIGHT_TORPEDO_BOATS"), value(parameters, "DARK_TORPEDO_BOATS"),
                value(parameters, "LIGHT_SUBMARINES"), value(parameters, "DARK_SUBMARINES"),
                value(parameters, "LIGHT_PLANES"), value(parameters, "DARK_PLANES"));
    }
    private static Integer value(List<GameConfigParameter> parameters, String name) {
        String value = parameters.stream().filter(p -> p.group() == GameConfigParameterGroup.FLEET && p.name().equals(name))
                .map(GameConfigParameter::value).findFirst().orElse(null);
        try { return new java.math.BigDecimal(value).intValueExact(); }
        catch (RuntimeException error) { return null; }
    }
    List<GameConfigParameter> applyTo(List<GameConfigParameter> parameters) {
        return parameters.stream().map(p -> p.group() != GameConfigParameterGroup.FLEET ? p
                : new GameConfigParameter(p.id(), p.group(), p.type(), p.name(), p.title(), p.description(),
                switch (p.name()) { case "LIGHT_TORPEDO_BOATS" -> lightTorpedoBoats.toString(); case "DARK_TORPEDO_BOATS" -> darkTorpedoBoats.toString();
                    case "LIGHT_SUBMARINES" -> lightSubmarines.toString(); case "DARK_SUBMARINES" -> darkSubmarines.toString();
                    case "LIGHT_PLANES" -> lightPlanes.toString(); case "DARK_PLANES" -> darkPlanes.toString();
                    default -> p.value(); })).toList();
    }
}
