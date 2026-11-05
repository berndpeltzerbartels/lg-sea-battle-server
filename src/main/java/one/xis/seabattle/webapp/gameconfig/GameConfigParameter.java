package one.xis.seabattle.webapp.gameconfig;

/** Public configuration value, also suitable for XIS form binding. */
public record GameConfigParameter(
        String id,
        GameConfigParameterGroup group,
        GameConfigParameterType type,
        String name,
        String title,
        String description,
        String value
) {
}
