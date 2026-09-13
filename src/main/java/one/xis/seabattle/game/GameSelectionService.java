package one.xis.seabattle.game;

import one.xis.context.Service;

import java.util.Optional;

@Service
class GameSelectionService {

    private static final String SETUP_ID = "selected.setup.id";
    private static final String LANDSCAPE_MODEL_ID = "selected.landscape.model.id";

    private final GamePropertyRepository repository;

    GameSelectionService(GamePropertyRepository repository) {
        this.repository = repository;
    }

    String selectedSetupId() {
        return value(SETUP_ID).filter(value -> !value.isBlank()).orElse("default");
    }

    Optional<String> selectedLandscapeModelId() {
        return value(LANDSCAPE_MODEL_ID).filter(value -> !value.isBlank());
    }

    void rememberSetupId(String setupId) {
        save(SETUP_ID, setupId == null || setupId.isBlank() ? "default" : setupId);
    }

    void rememberLandscapeModelId(String modelId) {
        save(LANDSCAPE_MODEL_ID, modelId);
    }

    void clearLandscapeModelId() {
        repository.deleteById(LANDSCAPE_MODEL_ID);
    }

    private Optional<String> value(String id) {
        return repository.findById(id).map(GamePropertyEntity::getPropertyValue);
    }

    private void save(String id, String value) {
        repository.save(new GamePropertyEntity(id, value));
    }
}
