package one.xis.seabattle.webapp.gameconfig;

import one.xis.UserContext;
import one.xis.context.Component;
import one.xis.validation.Validator;
import one.xis.validation.ValidatorException;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/** Only relationships between form fields; individual limits belong to @Range. */
@Component
class GameConfigFormValidator implements Validator<GameSettingsForm> {
    @Override
    public void validate(GameSettingsForm settings, AnnotatedElement element, UserContext user) throws ValidatorException {
        var form=settings.config();
        if (!form.complete()) return; // XIS reports missing fields at their inputs.
        String reason = form.closeFireArc() < form.fireArc()
                ? "Der Feuerwinkel im Nahbereich darf nicht enger sein als der normale Feuerwinkel."
                : form.fireMinRange() > form.fireMaxRange() || form.closeFireRange() > form.fireMaxRange()
                ? "Schussbereiche dürfen die maximale Schussweite nicht überschreiten."
                : !Double.isFinite(form.torpedoCooldownMin() + form.torpedoCooldownVariation())
                ? "Die gesamte Torpedopause ist zu groß." : null;
        if (reason != null) throw new ValidatorException(Map.of("reason", reason));
    }
}
