package one.xis.seabattle.webapp.admin;

import one.xis.Action;
import one.xis.ActionParameter;
import one.xis.FormData;
import one.xis.ModelData;
import one.xis.Page;
import one.xis.Roles;
import one.xis.ToastLevel;
import one.xis.ToastMessages;
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
    Class<?> uploadLandscape(@FormData("upload") LandscapeUploadForm form, ToastMessages toastMessages) {
        LandscapeModelService.LandscapeModelSummary model = landscapeModelService.saveUpload(form.getLandscapeFile());
        toastMessages.show("Landschaft gespeichert: " + model.name(), ToastLevel.SUCCESS);
        return SeaBattleAdminPage.class;
    }

    @Action("startSelection")
    Class<?> startSelection(@FormData("selection") GameSelectionForm form, ToastMessages toastMessages) {
        gameStateService.resetToSelection(form.getSetupId(), form.selectedLandscapeModelId());
        toastMessages.show("Spiel gestartet.", ToastLevel.SUCCESS);
        return SeaBattleAdminPage.class;
    }

    @Action("deleteLandscape")
    Class<?> deleteLandscape(@ActionParameter("modelId") String modelId, ToastMessages toastMessages) {
        boolean wasActiveLandscape = modelId != null && modelId.equals(gameStateService.landscapeModelId());
        if (wasActiveLandscape) {
            toastMessages.show("Aktive Landschaft kann nicht gelöscht werden.", ToastLevel.WARNING);
            return SeaBattleAdminPage.class;
        }
        boolean deleted = landscapeModelService.delete(modelId);
        if (deleted) {
            toastMessages.show("Landschaft gelöscht.", ToastLevel.SUCCESS);
        } else {
            toastMessages.show("Landschaft nicht gefunden.", ToastLevel.WARNING);
        }
        return SeaBattleAdminPage.class;
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
