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

    @FormData("selection")
    GameSelectionForm selection() {
        return new GameSelectionForm(gameStateService.setupId(), gameStateService.landscapeModelId());
    }

    @ModelData("landscapes")
    List<LandscapeOption> landscapes() {
        String activeLandscapeId = gameStateService.landscapeModelId();
        return landscapeModelService.summaries().stream()
                .map(model -> new LandscapeOption(
                        model.id(),
                        model.name(),
                        model.sourceFormat(),
                        model.landmassCount(),
                        model.createdAt(),
                        model.id().equals(activeLandscapeId),
                        !model.id().equals(activeLandscapeId)
                ))
                .toList();
    }

    @ModelData("setups")
    List<GameSetupOption> setups() {
        String activeSetupId = gameStateService.setupId();
        return List.of(
                setupOption("default", "Standardspiel", activeSetupId),
                setupOption("dense-land", "Dichte Landschaft", activeSetupId),
                setupOption("islands", "Offene Inseln", activeSetupId),
                setupOption("single-island", "Eine Insel", activeSetupId),
                setupOption("scout-plane", "Aufklärer", activeSetupId),
                setupOption("two-ship-duel", "Zwei-Schiff-Duell", activeSetupId),
                setupOption("two-ship-duel-air", "Duell mit Flugzeugen", activeSetupId),
                setupOption("landmark-tour", "Landmark-Tour", activeSetupId),
                setupOption("fleet-clash", "Flottenkollision", activeSetupId),
                setupOption("scenario-bomb-drop", "Bomben-Test", activeSetupId)
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

    @Action("startSelection")
    @ModelData("status")
    AdminStatus startSelection(@FormData("selection") GameSelectionForm form) {
        gameStateService.resetToSelection(form.getSetupId(), form.selectedLandscapeModelId());
        return currentStatus("Spiel gestartet.");
    }

    @Action("deleteLandscape")
    @ModelData("status")
    AdminStatus deleteLandscape(@ActionParameter("modelId") String modelId) {
        boolean wasActiveLandscape = modelId != null && modelId.equals(gameStateService.landscapeModelId());
        if (wasActiveLandscape) {
            return currentStatus("Aktive Landschaft kann nicht gelöscht werden.");
        }
        boolean deleted = landscapeModelService.delete(modelId);
        if (deleted) {
            return currentStatus("Landschaft gelöscht.");
        }
        return currentStatus("Landschaft nicht gefunden.");
    }

    private AdminStatus currentStatus(String message) {
        return new AdminStatus(
                gameStateService.setupId(),
                gameStateService.landscapeModelName(),
                gameStateService.landscapeModelId() == null,
                gameStateService.snapshot().ships().size(),
                gameStateService.landmassCount(),
                message
        );
    }

    private GameSetupOption setupOption(String id, String label, String activeSetupId) {
        return new GameSetupOption(id, label, id.equals(activeSetupId));
    }

    public record AdminStatus(String setupId, String landscapeName, boolean standardLandscape,
                              int shipCount, int landmassCount, String message) {
    }

    public record LandscapeOption(String id, String name, String sourceFormat, int landmassCount,
                                  java.time.LocalDateTime createdAt, boolean selected, boolean deletable) {
    }

    public record GameSetupOption(String id, String label, boolean selected) {
    }
}
