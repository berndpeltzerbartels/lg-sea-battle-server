package one.xis.seabattle.webapp.gameconfig;

import java.util.List;
import java.util.Objects;
import static one.xis.seabattle.webapp.gameconfig.GameConfigParameterType.*;

/** Independent submarine combat settings and depth/attack tactics. */
public record SubmarineBotConfig(BotCombatConfig combat,
        double diveRange,
        double deepRange,
        double safeRange,
        double shotAscentRange,
        double surfaceRange,
        double attackMinRange,
        double attackMaxRange,
        double aimTolerance,
        double aircraftMemorySeconds,
        double underwaterContactRange) {
    public SubmarineBotConfig { Objects.requireNonNull(combat); }
    public static SubmarineBotConfig defaults() {
        return new SubmarineBotConfig(BotCombatConfig.defaults().withAimErrorAngle(0), 500, 130, 220, 180, 650, 250, 400, 0.002617993877991494, 10, 849.555);
    }
    static SubmarineBotConfig fromParameters(List<GameConfigParameter> parameters) {
        var values = new GameConfigValues(parameters, GameConfigParameterGroup.SUBMARINE_BOT);
        return new SubmarineBotConfig(BotCombatConfig.fromParameters(parameters, GameConfigParameterGroup.SUBMARINE_BOT),
                values.number("DIVE_RANGE", DISTANCE),
                values.number("DEEP_RANGE", DISTANCE),
                values.number("SAFE_RANGE", DISTANCE),
                values.number("SHOT_ASCENT_RANGE", DISTANCE),
                values.number("SURFACE_RANGE", DISTANCE),
                values.number("ATTACK_MIN_RANGE", DISTANCE),
                values.number("ATTACK_MAX_RANGE", DISTANCE),
                values.number("AIM_TOLERANCE", ANGLE),
                values.number("AIRCRAFT_MEMORY_SECONDS", SECONDS),
                values.number("UNDERWATER_CONTACT_RANGE", DISTANCE));
    }
}
