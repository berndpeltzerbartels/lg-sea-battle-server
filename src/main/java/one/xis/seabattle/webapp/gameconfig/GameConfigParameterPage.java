package one.xis.seabattle.webapp.gameconfig;

import one.xis.*;
import one.xis.seabattle.webapp.admin.GameAdministration;
import one.xis.validation.Mandatory;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Page("/admin/game-config.html")
@Roles("ADMIN")
class GameConfigParameterPage {
    private final GameConfigService configuration;
    private final GameAdministration game;

    GameConfigParameterPage(GameConfigService configuration, GameAdministration game) {
        this.configuration = configuration;
        this.game = game;
    }

    @ModelData("parameters")
    Map<String, GameConfigParameter> parameters() {
        return configuration.findAll().stream()
                .filter(p -> p.group() == GameConfigParameterGroup.TORPEDO_BOAT_BOT)
                .map(this::displayParameter)
                .collect(Collectors.toMap(GameConfigParameter::name, Function.identity(), (first, duplicate) -> first));
    }

    private GameConfigParameter displayParameter(GameConfigParameter parameter) {
        String description = parameter.description().replace("in Radiant", "in Grad")
                .replace("in Welteinheiten", "in Metern").replaceAll(" Aktuell [^.]+\\.", "");
        return new GameConfigParameter(parameter.id(), parameter.group(), parameter.type(), parameter.name(),
                parameter.title(), description, parameter.value());
    }

    @FormData("config")
    GameConfigForm config() {
        return GameConfigForm.fromParameters(configuration.findAll());
    }

    @Action("save")
    void save(@FormData("config") GameConfigForm form, ToastMessages messages) {
        configuration.saveAll(form.applyTo(configuration.findAll()));
        messages.show("Gespeichert. Wird innerhalb von 30 Sekunden im laufenden Spiel übernommen.", ToastLevel.SUCCESS);
    }
    @FormData("fleetSize")
    FleetSizeForm fleetSize() { return FleetSizeForm.fromParameters(configuration.findAll()); }

    @Action("saveFleetSize")
    void saveFleetSize(@FormData("fleetSize") FleetSizeForm form, ToastMessages messages) {
        configuration.saveAll(form.applyTo(configuration.findAll()));
        messages.show("BURN_DOWN".equals(game.currentMode())
                ? "Flottengrößen gespeichert. Im Burn-down gelten sie ab der nächsten Runde."
                : "Flottengrößen gespeichert. Übernahme innerhalb von 30 Sekunden; Nachschub einzeln alle drei Sekunden.", ToastLevel.SUCCESS);
    }

    @ModelData("setups")
    List<GameAdministration.SetupOption> setups() { return game.setups(); }

    @FormData("gameSetup")
    GameSetupForm gameSetup() {
        String id = game.currentSetup();
        return new GameSetupForm(game.setups().stream().anyMatch(s -> s.id().equals(id)) ? id : "dense-land", game.currentMode());
    }

    @Action("restartGame")
    void restartGame(ToastMessages messages) {
        game.restart();
        messages.show("Spiel mit der bisherigen Aufstellung neu gestartet.", ToastLevel.SUCCESS);
    }

    @Action("selectSetup")
    void selectSetup(@FormData("gameSetup") GameSetupForm form, ToastMessages messages) {
        game.start(form.setupId(), form.mode());
        messages.show("Spiel mit der gewählten Landschaft und Aufstellung neu gestartet.", ToastLevel.SUCCESS);
    }

    record GameSetupForm(@Mandatory String setupId, @Mandatory String mode) {
        GameSetupForm(String setupId) { this(setupId, "ENDLESS"); }
    }

}
