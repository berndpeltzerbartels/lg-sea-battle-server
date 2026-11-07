package one.xis.seabattle.webapp.gameconfig;

import java.util.List;
import java.util.Objects;

/** Immutable values for one complete calculation round; capture the service reference once. */
public record GameRuntimeConfig(BotCombatConfig torpedoBoatBot, SubmarineBotConfig submarineBot, FleetSizeConfig fleetSize) {
    public GameRuntimeConfig {
        Objects.requireNonNull(torpedoBoatBot);
        Objects.requireNonNull(submarineBot);
    }

    public GameRuntimeConfig(BotCombatConfig torpedoBoatBot) { this(torpedoBoatBot, SubmarineBotConfig.defaults(), null); }
    public GameRuntimeConfig(BotCombatConfig torpedoBoatBot, FleetSizeConfig fleetSize) {
        this(torpedoBoatBot, SubmarineBotConfig.defaults(), fleetSize);
    }

    public static GameRuntimeConfig defaults() {
        return new GameRuntimeConfig(BotCombatConfig.defaults());
    }

    static GameRuntimeConfig fromParameters(List<GameConfigParameter> parameters) {
        return new GameRuntimeConfig(BotCombatConfig.fromParameters(parameters, GameConfigParameterGroup.TORPEDO_BOAT_BOT),
                SubmarineBotConfig.fromParameters(parameters),
                parameters.stream().anyMatch(p -> p.group() == GameConfigParameterGroup.FLEET)
                        ? FleetSizeConfig.fromParameters(parameters) : null);
    }
}
