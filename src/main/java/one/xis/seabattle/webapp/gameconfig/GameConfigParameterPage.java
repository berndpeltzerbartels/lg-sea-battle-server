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
    private final GameConfigProfileService profiles;

    GameConfigParameterPage(GameConfigService configuration, GameAdministration game, GameConfigProfileService profiles) {
        this.configuration = configuration;
        this.game = game;
        this.profiles = profiles;
    }

    @ModelData("profiles")
    List<GameConfigProfile.Summary> profiles() { return profiles.findAll(); }

    @FormData("profileSave")
    ProfileSaveForm profileSave() { return new ProfileSaveForm(""); }

    @FormData("profileLoad")
    ProfileLoadForm profileLoad() { return new ProfileLoadForm(""); }

    @Action("saveProfile")
    void saveProfile(@FormData("profileSave") ProfileSaveForm form,
                     @ClientState("adminGameConfigPreparedSetup") @NullAllowed GameSetupForm prepared, ToastMessages messages) {
        var selection = gameSetup(prepared);
        profiles.save(form.name(), configuration.findAll(), selection.setupId(), game.currentMode());
        messages.show("Profil gespeichert: " + form.name().strip(), ToastLevel.SUCCESS);
    }

    @Action("loadProfile")
    @ClientState("adminGameConfigPreparedSetup")
    @FormData("gameSetup")
    GameSetupForm loadProfile(@FormData("profileLoad") ProfileLoadForm form, ToastMessages messages) {
        var profile = profiles.findById(form.profileId()).orElseThrow(() ->
                new one.xis.validation.ValidationFailedException("/profileLoad/profileId", "gameConfig.profileMissing"));
        if (game.setups().stream().noneMatch(s -> s.id().equals(profile.setupId())))
            throw new one.xis.validation.ValidationFailedException("/profileLoad/profileId", "gameConfig.profileSetupMissing");
        var parameters = configuration.findAll().stream().map(p -> profile.values().containsKey(p.id())
                ? new GameConfigParameter(p.id(),p.group(),p.type(),p.name(),p.title(),p.description(),profile.values().get(p.id())) : p).toList();
        configuration.saveAll(parameters);
        messages.show("Profil geladen. Werte werden innerhalb von 30 Sekunden übernommen. Die Landschaft ist zum Starten ausgewählt; das Spiel wurde nicht neu gestartet.", ToastLevel.SUCCESS);
        return new GameSetupForm(profile.setupId());
    }

    record ProfileSaveForm(@Mandatory @one.xis.validation.RegExpr("(?s).{1,100}") String name) { }
    record ProfileLoadForm(@Mandatory String profileId) { }

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
        messages.show("Flottengrößen gespeichert. Übernahme innerhalb von 30 Sekunden; Nachschub einzeln alle drei Sekunden.", ToastLevel.SUCCESS);
    }

    @ModelData("setups")
    List<GameAdministration.SetupOption> setups() { return game.setups(); }

    @FormData("gameSetup")
    GameSetupForm gameSetup(@ClientState("adminGameConfigPreparedSetup") @NullAllowed GameSetupForm prepared) {
        if (prepared != null) return prepared;
        String id = game.currentSetup();
        return new GameSetupForm(game.setups().stream().anyMatch(s -> s.id().equals(id)) ? id : "dense-land");
    }

    @Action("restartGame")
    void restartGame(ToastMessages messages) {
        game.restart();
        messages.show("Spiel mit der bisherigen Aufstellung neu gestartet.", ToastLevel.SUCCESS);
    }

    @Action("selectSetup")
    @ClientState("adminGameConfigPreparedSetup")
    GameSetupForm selectSetup(@FormData("gameSetup") GameSetupForm form, ToastMessages messages) {
        game.selectSetup(form.setupId());
        messages.show("Spiel mit der gewählten Landschaft und Aufstellung neu gestartet.", ToastLevel.SUCCESS);
        return form;
    }

    record GameSetupForm(@Mandatory String setupId) { }

}
