package one.xis.seabattle.webapp.gameconfig;

import java.util.List;

/** Independent vehicle counts per colour, including vehicles controlled by humans. */
public record FleetSizeConfig(int lightTorpedoBoats, int darkTorpedoBoats, int lightSubmarines, int darkSubmarines,
                              int lightPlanes, int darkPlanes) {
    public int light() { return lightTorpedoBoats + lightSubmarines + lightPlanes; }
    public int dark() { return darkTorpedoBoats + darkSubmarines + darkPlanes; }
    public int total(String team) { return "light".equals(team) ? light() : dark(); }

    public int count(String team, String type) {
        boolean bright = "light".equals(team);
        return switch (type) {
            case "submarine" -> bright ? lightSubmarines : darkSubmarines;
            case "scout-plane" -> bright ? lightPlanes : darkPlanes;
            case "torpedo-boat" -> bright ? lightTorpedoBoats : darkTorpedoBoats;
            default -> 0;
        };
    }

    static FleetSizeConfig fromParameters(List<GameConfigParameter> parameters) {
        var values = new GameConfigValues(parameters, GameConfigParameterGroup.FLEET);
        return new FleetSizeConfig(values.integer("LIGHT_TORPEDO_BOATS"), values.integer("DARK_TORPEDO_BOATS"),
                values.integer("LIGHT_SUBMARINES"), values.integer("DARK_SUBMARINES"),
                values.integer("LIGHT_PLANES"), values.integer("DARK_PLANES"));
    }
}
