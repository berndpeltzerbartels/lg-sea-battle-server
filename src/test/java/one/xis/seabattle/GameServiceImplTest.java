package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GameServiceImplTest {

    @Test
    void closesPersistedActiveGamesBeforeStartingCurrentProcessGame() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        repository.save(new GameEntity("old-game", "ACTIVE", LocalDateTime.now().minusHours(1), null));
        RecordingPlaySessionService playSessionService = new RecordingPlaySessionService();
        GameServiceImpl service = new GameServiceImpl(repository, playSessionService);

        GameEntity current = service.activeGame();

        assertNotEquals("old-game", current.getId());
        assertEquals("ACTIVE", current.getStatus());
        assertEquals("ENDED", repository.findById("old-game").orElseThrow().getStatus());
        assertEquals(1, repository.findByStatus("ACTIVE").size());
        assertEquals(List.of("old-game"), playSessionService.closedGameIds);
    }

    private static class InMemoryGameRepository implements GameRepository {
        private final Map<String, GameEntity> games = new LinkedHashMap<>();

        @Override
        public List<GameEntity> findByStatus(String status) {
            return games.values().stream()
                    .filter(game -> status.equals(game.getStatus()))
                    .toList();
        }

        @Override
        public Optional<GameEntity> findById(String id) {
            return Optional.ofNullable(games.get(id));
        }

        @Override
        public List<GameEntity> findAll() {
            return new ArrayList<>(games.values());
        }

        @Override
        public GameEntity save(GameEntity entity) {
            games.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public boolean delete(GameEntity entity) {
            return entity != null && games.remove(entity.getId()) != null;
        }

        @Override
        public boolean deleteById(String id) {
            return games.remove(id) != null;
        }

        @Override
        public long count() {
            return games.size();
        }
    }

    private static class RecordingPlaySessionService implements PlaySessionService {
        private final List<String> closedGameIds = new ArrayList<>();

        @Override
        public void beginSession(String playerId, String accountId, String gameId, String alias, String teamId) {
        }

        @Override
        public void endSession(String playerId, int score) {
        }

        @Override
        public void endActiveSessionsForGame(String gameId, LocalDateTime endTime) {
            closedGameIds.add(gameId);
        }

        @Override
        public boolean isAliasActiveForOtherAccount(String gameId, String alias, String accountId) {
            return false;
        }
    }
}
