package one.xis.seabattle.webapp.gameconfig;

import one.xis.UseFormatter;
import one.xis.validation.Mandatory;
import one.xis.validation.LabelKey;
import one.xis.seabattle.webapp.validation.Range;
import one.xis.seabattle.webapp.validation.WholeNumberFormatter;
import java.util.List;

record FleetSizeForm(
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.light") Integer light,
        @Mandatory @Range(min = 0, max = 200) @UseFormatter(WholeNumberFormatter.class) @LabelKey("fleet.dark") Integer dark) {
    static FleetSizeForm fromParameters(List<GameConfigParameter> parameters) {
        return new FleetSizeForm(value(parameters, "LIGHT_VEHICLES"), value(parameters, "DARK_VEHICLES"));
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
                switch (p.name()) { case "LIGHT_VEHICLES" -> light.toString(); case "DARK_VEHICLES" -> dark.toString();
                    default -> p.value(); })).toList();
    }
}
