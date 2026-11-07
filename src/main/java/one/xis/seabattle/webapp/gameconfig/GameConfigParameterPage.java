package one.xis.seabattle.webapp.gameconfig;

import one.xis.*;
import one.xis.seabattle.webapp.admin.GameAdministration;
import one.xis.validation.Mandatory;
import one.xis.validation.ValidationFailedException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Page("/admin/game-config.html")
@Roles("ADMIN")
class GameConfigParameterPage {
    private static final String PAGE = "/admin/game-config.html";
    private static final String DRAFT = "adminGameConfigDraft";
    private final GameConfigService configuration;
    private final GameAdministration game;
    private final GameConfigProfileService profiles;
    private final GameConfigProfileDownloads downloads;

    GameConfigParameterPage(GameConfigService configuration, GameAdministration game, GameConfigProfileService profiles, GameConfigProfileDownloads downloads) {
        this.configuration = configuration;
        this.game = game;
        this.profiles = profiles;
        this.downloads = downloads;
    }

    @ModelData("profiles")
    List<GameConfigProfile.Summary> profiles() {
        return profiles.findAll();
    }

    @ModelData("setups")
    List<GameAdministration.SetupOption> setups() {
        return game.setups();
    }

    @ModelData("parameters")
    Map<String, GameConfigParameter> parameters() {
        return configuration.findAll().stream().filter(p -> p.group() == GameConfigParameterGroup.TORPEDO_BOAT_BOT)
                .map(this::displayParameter).collect(Collectors.toMap(GameConfigParameter::name, Function.identity()));
    }

    @FormData("settings")
    GameSettingsForm settings(@QueryParameter("profile") @NullAllowed String id,
                              @ClientState(DRAFT) @NullAllowed GameSettingsForm draft) {
        if (draft != null) return draft;
        if (id != null && !id.isBlank()) {
            var profile = profile(id);
            return GameSettingsForm.from(profile.id(), profile.name(), profile.setupId(), profile.mode(), overlay(profile.values()));
        }
        String setup = game.currentSetup();
        if (game.setups().stream().noneMatch(s -> s.id().equals(game.currentSetup()))) setup = "dense-land";
        return GameSettingsForm.from(null, "", setup, game.currentMode(), configuration.findAll());
    }

    @FormData("profileLoad")
    ProfileLoadForm profileLoad(@QueryParameter("profile") @NullAllowed String id) {
        return new ProfileLoadForm(id == null ? "" : id);
    }

    @FormData("fileImport")
    FileImportForm fileImport() {
        return new FileImportForm();
    }

    @Action("loadProfile")
    PageUrlResponse loadProfile(@FormData("profileLoad") ProfileLoadForm form) {
        profile(form.profileId());
        return edit(form.profileId()).clientState(DRAFT, null);
    }

    @Action("newProfile")
    PageUrlResponse newProfile() {
        return edit(null).clientState(DRAFT, null);
    }

    @Action("copyProfile")
    PageUrlResponse copyProfile(@FormData("settings") GameSettingsForm form, ToastMessages messages) {
        String base = form.name() == null || form.name().isBlank() ? "Profil" : form.name().strip();
        if (base.length() > 80) base = base.substring(0, 80);
        String name = base + " (Kopie)";
        int suffix = 2;
        Set<String> names = profiles.findAll().stream().map(p -> p.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        while (names.contains(name.toLowerCase(Locale.ROOT))) name = base + " (Kopie " + suffix++ + ")";
        var copy = profiles.save(null, name, form.parameters(configuration.findAll()), form.setupId(), form.mode());
        messages.show("Kopie angelegt: " + copy.name(), ToastLevel.SUCCESS);
        return edit(copy.id()).clientState(DRAFT, null);
    }

    @Action("save")
    PageUrlResponse save(@FormData("settings") GameSettingsForm form, ToastMessages messages) {
        var parameters = form.parameters(configuration.findAll());
        var saved = profiles.save(form.profileId(), form.name(), parameters, form.setupId(), form.mode());
        configuration.saveAll(parameters);
        messages.show("Profil gespeichert. Bot- und Flottenwerte werden innerhalb von 30 Sekunden übernommen.", ToastLevel.SUCCESS);
        return edit(saved.id()).clientState(DRAFT, null);
    }

    @Action("selectSetup")
    @ClientState(DRAFT)
    @FormData("settings")
    GameSettingsForm selectSetup(@FormData("settings") GameSettingsForm form, ToastMessages messages) {
        configuration.saveAll(form.parameters(configuration.findAll()));
        game.start(form.setupId(), form.mode());
        messages.show("Spiel mit diesen Einstellungen neu gestartet.", ToastLevel.SUCCESS);
        return form;
    }

    @Action("restartGame")
    void restartGame(ToastMessages messages) {
        game.restart();
        messages.show("Aktuelles Spiel neu gestartet.", ToastLevel.SUCCESS);
    }

    @Action("exportProfile")
    PageUrlResponse exportProfile(@FormData("settings") GameSettingsForm form) {
        String json = GameConfigProfileJson.write(form.name(), form.setupId(), form.mode(), form.parameters(configuration.findAll()));
        return new PageUrlResponse(GameConfigProfileDownloadController.PATH + downloads.prepare(form.name(), json));
    }

    @Action("importFile")
    @ClientState(DRAFT)
    @FormData("settings")
    GameSettingsForm importFile(@FormData("fileImport") FileImportForm form) {
        return importJson(form.json);
    }

    private GameSettingsForm importJson(String json) {
        var imported = GameConfigProfileJson.read(json, "/fileImport/json");
        if (game.setups().stream().noneMatch(s -> s.id().equals(imported.setupId())))
            throw new ValidationFailedException("/fileImport/json", "gameConfig.profileSetupMissing");
        return GameSettingsForm.from(null, imported.name(), imported.setupId(), imported.mode(), overlay(imported.parameters()));
    }

    private GameConfigParameter displayParameter(GameConfigParameter parameter) {
        String description = parameter.description().replace("in Radiant", "in Grad")
                .replace("in Welteinheiten", "in Metern").replaceAll(" Aktuell [^.]+\\.", "");
        return new GameConfigParameter(parameter.id(), parameter.group(), parameter.type(), parameter.name(), parameter.title(), description, parameter.value());
    }

    private GameConfigProfile profile(String id) {
        return profiles.findById(id).orElseThrow(() -> new ValidationFailedException("/profileLoad/profileId", "gameConfig.profileMissing"));
    }

    private List<GameConfigParameter> overlay(Map<String, String> values) {
        return configuration.findAll().stream().map(p -> values.containsKey(p.id())
                ? new GameConfigParameter(p.id(), p.group(), p.type(), p.name(), p.title(), p.description(), values.get(p.id())) : p).toList();
    }

    private PageUrlResponse edit(String id) {
        return id == null || id.isBlank() ? new PageUrlResponse(PAGE) : new PageUrlResponse(PAGE, Map.of("profile", id));
    }

    record ProfileLoadForm(@Mandatory String profileId) {
    }

    static class FileImportForm {
        @Upload(maxSize = 1024 * 1024)
        public String json;
    }
}
