package one.xis.seabattle.game;

import one.xis.seabattle.webapp.gameconfig.*;
import java.util.List;
import java.util.Optional;

/** Fixed configuration for simulations that do not exercise persistence or live reload. */
final class TestGameConfigService implements GameConfigService {
    private static final TestGameConfigService INSTANCE = new TestGameConfigService(() -> GameSession.DEFAULT_RUNTIME_CONFIG);
    private final java.util.function.Supplier<GameRuntimeConfig> configuration;
    private TestGameConfigService(java.util.function.Supplier<GameRuntimeConfig> configuration) {
        this.configuration = configuration;
    }
    static GameConfigService from(java.util.function.Supplier<GameRuntimeConfig> configuration) {
        return new TestGameConfigService(configuration);
    }
    static GameConfigService defaults() { return INSTANCE; }
    @Override public GameRuntimeConfig current() { return configuration.get(); }
    @Override public List<GameConfigParameter> findAll() { throw new UnsupportedOperationException(); }
    @Override public Optional<GameConfigParameter> findById(String id) { throw new UnsupportedOperationException(); }
    @Override public GameConfigParameter save(GameConfigParameter parameter) { throw new UnsupportedOperationException(); }
    @Override public boolean deleteById(String id) { throw new UnsupportedOperationException(); }
}
