package one.xis.seabattle.webapp.validation;

import one.xis.Formatter;
import java.time.ZoneId;
import java.util.Locale;

/** HTML number inputs submit an ungrouped integer, independently of the browser locale. */
public class WholeNumberFormatter implements Formatter<Integer> {
    @Override public String format(Integer value, Locale locale, ZoneId zone) {
        return value.toString();
    }
    @Override public Integer parse(String value, Locale locale, ZoneId zone) {
        if (!value.matches("-?[0-9]+")) throw new IllegalArgumentException("Ganze Zahl erforderlich.");
        return Integer.valueOf(value);
    }
}
