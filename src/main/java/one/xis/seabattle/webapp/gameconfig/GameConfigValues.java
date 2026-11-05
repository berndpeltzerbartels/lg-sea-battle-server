package one.xis.seabattle.webapp.gameconfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parsing/metadata checks happen only on reload, never in the game loop. */
final class GameConfigValues {
    private final Map<String, GameConfigParameter> parameters = new HashMap<>();

    GameConfigValues(List<GameConfigParameter> all, GameConfigParameterGroup group) {
        for (GameConfigParameter parameter : all) {
            if (parameter.group() != group) continue;
            if (parameters.putIfAbsent(parameter.name(), parameter) != null)
                throw new IllegalArgumentException("Doppelter Parameter: " + group + "." + parameter.name());
        }
    }

    double number(String name, GameConfigParameterType type) {
        GameConfigParameter parameter = parameters.get(name);
        if (parameter == null) throw new IllegalArgumentException("Fehlender Parameter: " + name);
        if (parameter.type() != type)
            throw new IllegalArgumentException("Falscher Parametertyp für " + name + ": erwartet " + type);
        try {
            double number = Double.parseDouble(parameter.value());
            if (!Double.isFinite(number)) throw new NumberFormatException();
            return number;
        } catch (NullPointerException | NumberFormatException e) {
            throw new IllegalArgumentException("Ungültiger Zahlenwert für " + name, e);
        }
    }

    int integer(String name) {
        double number = number(name, GameConfigParameterType.NUMBER);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            throw new IllegalArgumentException(name + " muss eine ganze Zahl im Integer-Bereich sein.");
        return (int) number;
    }
}
