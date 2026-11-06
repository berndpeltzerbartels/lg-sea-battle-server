package one.xis.seabattle.webapp.gameconfig;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import one.xis.context.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Profile I/O is only used by administration, never by the calculation loop or its scheduler. */
@Service
class GameConfigProfileServiceImpl implements GameConfigProfileService {
    private static final Gson JSON = new Gson();
    private final GameConfigProfileRepository repository;

    GameConfigProfileServiceImpl(GameConfigProfileRepository repository) { this.repository = repository; }

    @Override public List<GameConfigProfile.Summary> findAll() {
        return repository.findAll().stream().map(p -> new GameConfigProfile.Summary(p.getId(),p.getName()))
                .sorted(Comparator.comparing(GameConfigProfile.Summary::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Override public Optional<GameConfigProfile> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return repository.findById(id).map(this::toProfile);
    }

    @Override public GameConfigProfile save(String id, String name, List<GameConfigParameter> parameters, String setupId, String mode) {
        String title = validName(name), key = title.toLowerCase(Locale.ROOT);
        if (id != null && !id.isBlank() && repository.findById(id).isEmpty())
            throw new IllegalArgumentException("Profil nicht mehr vorhanden.");
        String profileId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        ensureUniqueName(key, profileId);
        Map<String,String> values = parameters.stream().collect(Collectors.toMap(GameConfigParameter::id, GameConfigParameter::value));
        var entity = new GameConfigProfileEntity(profileId,title,key,JSON.toJson(values),setupId,mode);
        try { return toProfile(repository.save(entity)); }
        catch (RuntimeException failure) { ensureUniqueName(key,profileId); throw failure; }
    }

    private String validName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100)
            throw new one.xis.validation.ValidationFailedException("/settings/name", "gameConfig.profileNameInvalid");
        return name.strip();
    }

    private void ensureUniqueName(String key, String id) {
        if (repository.findAll().stream().anyMatch(p -> !p.getId().equals(id) && key.equals(p.getNameKey())))
            throw new one.xis.validation.ValidationFailedException("/settings/name", "gameConfig.profileNameTaken");
    }

    private GameConfigProfile toProfile(GameConfigProfileEntity entity) {
        Map<String,String> values = JSON.fromJson(entity.getParameterValues(),new TypeToken<Map<String,String>>(){}.getType());
        return new GameConfigProfile(entity.getId(),entity.getName(),values,entity.getSetupId(),entity.getMode());
    }
}
