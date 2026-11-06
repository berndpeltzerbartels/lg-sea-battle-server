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

    @Override public GameConfigProfile save(String name, List<GameConfigParameter> parameters, String setupId, String mode) {
        if (name == null || name.isBlank() || name.strip().length() > 100)
            throw new IllegalArgumentException("Ein Profilname mit höchstens 100 Zeichen ist erforderlich.");
        String title = name.strip();
        Map<String,String> values = parameters.stream().collect(Collectors.toMap(GameConfigParameter::id, GameConfigParameter::value));
        var entity = new GameConfigProfileEntity(title.toLowerCase(Locale.ROOT),title,JSON.toJson(values),setupId,mode);
        return toProfile(repository.save(entity));
    }

    private GameConfigProfile toProfile(GameConfigProfileEntity entity) {
        Map<String,String> values = JSON.fromJson(entity.getParameterValues(),new TypeToken<Map<String,String>>(){}.getType());
        return new GameConfigProfile(entity.getId(),entity.getName(),values,entity.getSetupId(),entity.getMode());
    }
}
