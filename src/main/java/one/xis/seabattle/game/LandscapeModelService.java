package one.xis.seabattle.game;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import one.xis.UploadedFile;
import one.xis.context.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LandscapeModelService {

    private final LandscapeModelRepository repository;
    private final LandscapeModelConverter converter = new LandscapeModelConverter();
    private final Gson gson = new Gson();

    LandscapeModelService(LandscapeModelRepository repository) {
        this.repository = repository;
    }

    public List<LandscapeModelSummary> summaries() {
        return repository.findAllNewestFirst().stream()
                .map(this::summary)
                .toList();
    }

    public Optional<StoredLandscapeModel> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(id)
                .map(entity -> new StoredLandscapeModel(entity.getId(), entity.getName(), worldMap(entity)));
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return repository.deleteById(id);
    }

    public LandscapeModelSummary saveUpload(UploadedFile file) {
        if (file == null || file.getSize() == 0) {
            throw new IllegalArgumentException("Bitte eine JSON-Datei auswählen.");
        }
        String json = file.getUtf8Text();
        JsonObject root = parseRoot(json);
        WorldMap worldMap = converter.convertEditorLandscape(root);
        String name = stringValue(root, "name").orElse(fileNameWithoutExtension(file.getFileName()));
        LandscapeModelEntity entity = new LandscapeModelEntity(
                UUID.randomUUID().toString(),
                name == null || name.isBlank() ? "Unbenannte Landschaft" : name.trim(),
                stringValue(root, "format").orElse("game-landscape-designer.v1"),
                json,
                gson.toJson(worldMap),
                LocalDateTime.now()
        );
        repository.save(entity);
        return summary(entity);
    }

    private LandscapeModelSummary summary(LandscapeModelEntity entity) {
        WorldMap worldMap = worldMap(entity);
        return new LandscapeModelSummary(
                entity.getId(),
                entity.getName(),
                entity.getSourceFormat(),
                worldMap.landmasses().size(),
                entity.getCreatedAt()
        );
    }

    private WorldMap worldMap(LandscapeModelEntity entity) {
        return converter.convertEditorLandscape(parseRoot(entity.getOriginalJson()));
    }

    private JsonObject parseRoot(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("Die Datei ist kein gültiges Landschafts-JSON.");
            }
            return root.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("Die Datei ist kein gültiges Landschafts-JSON.", e);
        }
    }

    private Optional<String> stringValue(JsonObject object, String property) {
        JsonElement element = object.get(property);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    private String fileNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Unbenannte Landschaft";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public record LandscapeModelSummary(String id, String name, String sourceFormat, int landmassCount,
                                        LocalDateTime createdAt) {
    }

    public record StoredLandscapeModel(String id, String name, WorldMap worldMap) {
    }
}
