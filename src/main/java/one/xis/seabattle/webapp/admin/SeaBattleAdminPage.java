package one.xis.seabattle.webapp.admin;

import one.xis.Action;
import one.xis.ActionParameter;
import one.xis.FormData;
import one.xis.ModelData;
import one.xis.Page;
import one.xis.Roles;
import one.xis.seabattle.game.GameStateService;
import one.xis.seabattle.game.LandscapeModelService;

import java.util.List;

@Page("/admin.html")
@Roles({"ADMIN", "admin"})
public class SeaBattleAdminPage {

    private final LandscapeModelService landscapeModelService;
    private final GameStateService gameStateService;

    public SeaBattleAdminPage(LandscapeModelService landscapeModelService, GameStateService gameStateService) {
        this.landscapeModelService = landscapeModelService;
        this.gameStateService = gameStateService;
    }

    @FormData("upload")
    LandscapeUploadForm upload() {
        return new LandscapeUploadForm();
    }

    @ModelData("landscapes")
    List<LandscapeModelService.LandscapeModelSummary> landscapes() {
        return landscapeModelService.summaries();
    }

    @ModelData("setups")
    List<GameSetupOption> setups() {
        return List.of(
                new GameSetupOption("default", "Standardspiel"),
                new GameSetupOption("dense-land", "Dichte Landschaft"),
                new GameSetupOption("islands", "Offene Inseln"),
                new GameSetupOption("single-island", "Eine Insel"),
                new GameSetupOption("scout-plane", "Aufklärer"),
                new GameSetupOption("two-ship-duel", "Zwei-Schiff-Duell"),
                new GameSetupOption("two-ship-duel-air", "Duell mit Flugzeugen"),
                new GameSetupOption("landmark-tour", "Landmark-Tour"),
                new GameSetupOption("fleet-clash", "Flottenkollision"),
                new GameSetupOption("scenario-bomb-drop", "Bomben-Test")
        );
    }

    @ModelData("status")
    AdminStatus status() {
        return currentStatus("");
    }

    @Action("uploadLandscape")
    @ModelData("status")
    AdminStatus uploadLandscape(@FormData("upload") LandscapeUploadForm form) {
        LandscapeModelService.LandscapeModelSummary model = landscapeModelService.saveUpload(form.getLandscapeFile());
        return currentStatus("Landschaft gespeichert: " + model.name());
    }

    @Action("restartLandscape")
    @ModelData("status")
    AdminStatus restartLandscape(@ActionParameter("modelId") String modelId) {
        gameStateService.resetToLandscapeModel(modelId);
        return currentStatus("Spiel mit Landschaft gestartet.");
    }

    @Action("deleteLandscape")
    @ModelData("status")
    AdminStatus deleteLandscape(@ActionParameter("modelId") String modelId) {
        boolean wasActiveLandscape = modelId != null && modelId.equals(gameStateService.landscapeModelId());
        boolean deleted = landscapeModelService.delete(modelId);
        if (deleted && wasActiveLandscape) {
            gameStateService.resetToSetup("default");
        }
        if (deleted) {
            return currentStatus("Landschaft gelöscht.");
        }
        return currentStatus("Landschaft nicht gefunden.");
    }

    @Action("restartSetup")
    @ModelData("status")
    AdminStatus restartSetup(@ActionParameter("setupId") String setupId) {
        gameStateService.resetToSetup(setupId);
        return currentStatus("Spielset gestartet: " + setupId);
    }

    private AdminStatus currentStatus(String message) {
        return new AdminStatus(
                gameStateService.setupId(),
                gameStateService.landscapeModelName(),
                gameStateService.snapshot().ships().size(),
                gameStateService.landmassCount(),
                message
        );
    }

    public record AdminStatus(String setupId, String landscapeName, int shipCount, int landmassCount, String message) {
    }

    public record GameSetupOption(String id, String label) {
    }
}
