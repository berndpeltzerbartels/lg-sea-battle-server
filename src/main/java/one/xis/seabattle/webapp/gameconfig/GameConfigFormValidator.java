package one.xis.seabattle.webapp.gameconfig;

import one.xis.UserContext;
import one.xis.context.Component;
import one.xis.validation.Validator;
import one.xis.validation.ValidatorException;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/** Cross-field relations only; individual ranges live on the form fields. */
@Component
class GameConfigFormValidator implements Validator<GameSettingsForm> {
    @Override public void validate(GameSettingsForm settings, AnnotatedElement element, UserContext user) throws ValidatorException {
        validateCombat(settings.config(), "Torpedoboot");
        validateCombat(settings.submarineCombat(), "U-Boot");
        var sub=settings.submarineTactics();
        if (!sub.complete()) return;
        String reason = sub.attackMinRange() > sub.attackMaxRange() ? "Der untere U-Boot-Angriffsabstand darf den oberen nicht überschreiten."
                : sub.deepRange() > sub.diveRange() || sub.diveRange() > sub.surfaceRange()
                ? "U-Boot-Tauchgrenzen müssen in der Reihenfolge Tieftauchen, Sehrohrtiefe, Auftauchen liegen." : null;
        if (reason != null) throw new ValidatorException(Map.of("reason",reason));
    }
    private void validateCombat(GameConfigForm form, String vehicle) throws ValidatorException {
        if (!form.complete()) return;
        String reason = form.closeFireArc() < form.fireArc()
                ? "Der Feuerwinkel im Nahbereich darf nicht enger sein als der normale Feuerwinkel."
                : form.fireMinRange() > form.fireMaxRange() || form.closeFireRange() > form.fireMaxRange()
                ? "Schussbereiche dürfen die maximale Schussweite nicht überschreiten."
                : !Double.isFinite(form.torpedoCooldownMin() + form.torpedoCooldownVariation())
                ? "Die gesamte Torpedopause ist zu groß." : null;
        if (reason != null) throw new ValidatorException(Map.of("reason",vehicle+": "+reason));
    }
}
