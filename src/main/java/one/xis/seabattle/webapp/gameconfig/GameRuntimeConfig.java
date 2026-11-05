package one.xis.seabattle.webapp.gameconfig;

import java.util.List;
import java.util.Objects;

/** Immutable values for one complete calculation round; capture the service reference once. */
public record GameRuntimeConfig(TorpedoBoatBotConfig torpedoBoatBot, FleetSizeConfig fleetSize) {
    public GameRuntimeConfig {
        Objects.requireNonNull(torpedoBoatBot);
    }

    public GameRuntimeConfig(TorpedoBoatBotConfig torpedoBoatBot) { this(torpedoBoatBot, null); }

    public static GameRuntimeConfig defaults() {
        return new GameRuntimeConfig(TorpedoBoatBotConfig.defaults());
    }

    static GameRuntimeConfig fromParameters(List<GameConfigParameter> parameters) {
        return new GameRuntimeConfig(TorpedoBoatBotConfig.fromParameters(parameters),
                parameters.stream().anyMatch(p -> p.group() == GameConfigParameterGroup.FLEET)
                        ? FleetSizeConfig.fromParameters(parameters) : null);
    }
}
