package one.xis.seabattle.webapp.gameconfig;

import one.xis.context.Service;
import one.xis.context.Scheduled;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
class GameConfigServiceImpl implements GameConfigService {
    private static final System.Logger LOG = System.getLogger(GameConfigServiceImpl.class.getName());
    private final GameConfigParameterRepository repository;
    private final GameConfigParameterMapper mapper = GameConfigParameterMapper.INSTANCE;
    private volatile GameRuntimeConfig current = GameRuntimeConfig.defaults();

    GameConfigServiceImpl(GameConfigParameterRepository repository) {
        this.repository = repository;
    }

    @Override
    public GameRuntimeConfig current() {
        return current;
    }

    // The XIS scheduler is the sole writer. The first invocation runs after context initialization.
    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void refresh() {
        try {
            GameRuntimeConfig loaded = GameRuntimeConfig.fromParameters(findAll());
            if (!loaded.equals(current)) current = loaded;
        } catch (RuntimeException error) {
            LOG.log(System.Logger.Level.WARNING,
                    "Spielkonfiguration nicht aktualisiert; letzter gültiger Stand bleibt aktiv.", error);
        }
    }

    @Override
    public List<GameConfigParameter> findAll() {
        return repository.findAll().stream().map(mapper::toParameter).toList();
    }

    @Override
    public Optional<GameConfigParameter> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return repository.findById(id).map(mapper::toParameter);
    }

    @Override
    public GameConfigParameter save(GameConfigParameter parameter) {
        return mapper.toParameter(repository.save(mapper.toEntity(parameter)));
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.isBlank()) return false;
        return repository.deleteById(id);
    }
}
