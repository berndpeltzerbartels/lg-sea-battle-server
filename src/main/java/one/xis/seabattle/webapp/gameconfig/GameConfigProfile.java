package one.xis.seabattle.webapp.gameconfig;

import java.util.Map;

/** Saved parameter values, independent of later changes to defaults and descriptions. */
public record GameConfigProfile(String id, String name, Map<String, String> values, String setupId, String mode) {
    public GameConfigProfile { values = Map.copyOf(values); }
    public record Summary(String id, String name) { }
}
