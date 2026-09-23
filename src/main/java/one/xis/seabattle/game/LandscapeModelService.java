package one.xis.seabattle.game;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import one.xis.UploadedFile;
import one.xis.context.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LandscapeModelService {

    private static final String SUPPORTED_SOURCE_FORMAT = "game-landscape-designer.v2";
    // Bump when conversion, collision preparation or respawn generation changes.
    private static final int PREPARATION_VERSION = 1;

    private final LandscapeModelRepository repository;
    private final LandscapeModelConverter converter = new LandscapeModelConverter();
    private final Gson gson = new Gson();
    private final Map<String, StoredLandscapeModel> preparedModels = new ConcurrentHashMap<>();

    LandscapeModelService(LandscapeModelRepository repository) {
        this.repository = repository;
    }

    public List<LandscapeModelSummary> summaries() {
        return repository.findAllNewestFirst().stream()
                .map(this::summary)
                .sorted(Comparator.comparing(LandscapeModelSummary::createdAt).reversed())
                .toList();
    }

    public Optional<StoredLandscapeModel> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        StoredLandscapeModel prepared = preparedModels.get(id);
        if (prepared != null) {
            return Optional.of(prepared);
        }
        return repository.findById(id).flatMap(this::storedModel);
    }

    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean deleted = repository.deleteById(id);
        if (deleted) {
            preparedModels.remove(id);
        }
        return deleted;
    }

    public LandscapeModelSummary saveUpload(UploadedFile file) {
        if (file == null || file.getSize() == 0) {
            throw new IllegalArgumentException("Bitte eine JSON-Datei auswählen.");
        }
        String json = file.getUtf8Text();
        JsonObject root = parseRoot(json);
        String sourceFormat = stringValue(root, "format")
                .orElseThrow(() -> new IllegalArgumentException("Die Landschaft braucht das Format " + SUPPORTED_SOURCE_FORMAT + "."));
        if (!SUPPORTED_SOURCE_FORMAT.equals(sourceFormat)) {
            throw new IllegalArgumentException("Die Landschaft braucht das Format " + SUPPORTED_SOURCE_FORMAT + ".");
        }
        WorldMap worldMap = converter.convertEditorLandscape(root);
        List<Vector2> respawnCandidates = DefaultGameSetupFactory.generatedWaterRespawnCandidates(worldMap);
        String name = stringValue(root, "name").orElse(fileNameWithoutExtension(file.getFileName()));
        LocalDateTime createdAt = timestampValue(root, "createdAt").orElseGet(LocalDateTime::now);
        LandscapeModelEntity entity = new LandscapeModelEntity(
                UUID.randomUUID().toString(),
                name == null || name.isBlank() ? "Unbenannte Landschaft" : name.trim(),
                sourceFormat,
                json,
                gson.toJson(worldMap),
                gson.toJson(respawnCandidates),
                createdAt
        );
        writePreparedMap(entity, worldMap);
        repository.save(entity);
        preparedModels.put(entity.getId(), new StoredLandscapeModel(entity.getId(), entity.getName(), worldMap, respawnCandidates));
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
        StoredLandscapeModel cached = preparedModels.get(entity.getId());
        if (cached != null) return cached.worldMap();
        return storedModel(entity).orElseThrow().worldMap();
    }

    private Optional<StoredLandscapeModel> storedModel(LandscapeModelEntity entity) {
        WorldMap worldMap = null;
        List<Vector2> respawnCandidates = null;
        try {
            JsonObject stored = parseRoot(entity.getWorldMapJson());
            JsonObject preparation = stored.getAsJsonObject("preparation");
            stored.remove("preparation");
            if (preparation != null
                    && preparation.get("version").getAsInt() == PREPARATION_VERSION
                    && preparation.get("sourceSha256").getAsString().equals(sha256(entity.getOriginalJson()))
                    && preparation.get("worldMapSha256").getAsString().equals(sha256(stored.toString()))
                    && preparation.get("respawnSha256").getAsString().equals(sha256(entity.getRespawnCandidatesJson()))) {
                WorldMap decoded = gson.fromJson(stored, WorldMap.class);
                Vector2[] candidates = gson.fromJson(entity.getRespawnCandidatesJson(), Vector2[].class);
                if (decoded != null && !decoded.landmasses().isEmpty() && candidates != null
                        && candidates.length > 0 && java.util.Arrays.stream(candidates)
                        .allMatch(point -> point != null && Double.isFinite(point.x()) && Double.isFinite(point.z()))) {
                    worldMap = decoded;
                    respawnCandidates = List.of(candidates);
                }
            }
        } catch (IllegalArgumentException | JsonParseException | IllegalStateException | NullPointerException | ClassCastException e) {
            // Legacy or damaged derived data is rebuilt from the unchanged original.
        }
        if (worldMap == null) {
            worldMap = converter.convertEditorLandscape(parseRoot(entity.getOriginalJson()));
            respawnCandidates = DefaultGameSetupFactory.generatedWaterRespawnCandidates(worldMap);
            entity.setRespawnCandidatesJson(gson.toJson(respawnCandidates));
            writePreparedMap(entity, worldMap);
            repository.save(entity);
        }
        StoredLandscapeModel model = new StoredLandscapeModel(
                entity.getId(),
                entity.getName(),
                worldMap,
                respawnCandidates
        );
        preparedModels.put(entity.getId(), model);
        return Optional.of(model);
    }

    private void writePreparedMap(LandscapeModelEntity entity, WorldMap worldMap) {
        JsonObject stored = gson.toJsonTree(worldMap).getAsJsonObject();
        JsonObject preparation = new JsonObject();
        preparation.addProperty("version", PREPARATION_VERSION);
        preparation.addProperty("sourceSha256", sha256(entity.getOriginalJson()));
        preparation.addProperty("worldMapSha256", sha256(stored.toString()));
        preparation.addProperty("respawnSha256", sha256(entity.getRespawnCandidatesJson()));
        stored.add("preparation", preparation);
        entity.setWorldMapJson(gson.toJson(stored));
    }

    private static String sha256(String value) {
        if (value == null) return "";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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

    private Optional<LocalDateTime> timestampValue(JsonObject object, String property) {
        return stringValue(object, property).flatMap(value -> {
            try {
                return Optional.of(OffsetDateTime.parse(value)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime());
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        });
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

    public record StoredLandscapeModel(String id, String name, WorldMap worldMap, List<Vector2> respawnCandidates) {
        public StoredLandscapeModel {
            respawnCandidates = respawnCandidates == null ? List.of() : List.copyOf(respawnCandidates);
        }
    }
}
