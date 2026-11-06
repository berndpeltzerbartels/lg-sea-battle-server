package one.xis.seabattle.webapp.gameconfig;

import java.util.List;
import java.util.Optional;

public interface GameConfigService {
    /** Memory-only access. Hold this immutable object for the whole calculation round. */
    GameRuntimeConfig current();

    /** Explicit round starts read saved values once, independent of the live refresh interval. */
    default GameRuntimeConfig forNewRound() { return current(); }

    List<GameConfigParameter> findAll();

    Optional<GameConfigParameter> findById(String id);

    GameConfigParameter save(GameConfigParameter parameter);

    /** All form values become visible to the scheduler together, after commit. */
    @one.xis.sql.Transactional
    default void saveAll(List<GameConfigParameter> parameters) {
        parameters.forEach(this::save);
    }

    boolean deleteById(String id);
}
