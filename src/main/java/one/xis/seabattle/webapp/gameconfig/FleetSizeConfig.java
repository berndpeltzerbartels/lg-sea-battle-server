package one.xis.seabattle.webapp.gameconfig;

import java.util.List;

/** Active vehicles per colour, including human players and aircraft. */
public record FleetSizeConfig(int light, int dark) {
    static FleetSizeConfig fromParameters(List<GameConfigParameter> parameters) {
        var values = new GameConfigValues(parameters, GameConfigParameterGroup.FLEET);
        return new FleetSizeConfig(values.integer("LIGHT_VEHICLES"), values.integer("DARK_VEHICLES"));
    }
}
