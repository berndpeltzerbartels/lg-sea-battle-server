package one.xis.seabattle;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaySessionServiceImplTest {

    @Test
    void treatsAliasAsActiveOnlyForOtherAccountsInSameActiveGame() {
        InMemoryPlaySessionRepository repository = new InMemoryPlaySessionRepository();
        PlaySessionServiceImpl service = new PlaySessionServiceImpl(repository);

        service.beginSession("player-BPB-123", "account-1", "game-1", "BPB", "light");

        assertFalse(service.isAliasActiveForOtherAccount("game-1", "BPB", "account-1"));
        assertTrue(service.isAliasActiveForOtherAccount("game-1", "BPB", "account-2"));
        assertFalse(service.isAliasActiveForOtherAccount("game-2", "BPB", "account-2"));

        service.endSession("player-BPB-123", 4);

        assertFalse(service.isAliasActiveForOtherAccount("game-1", "BPB", "account-2"));
    }

    @Test
    void reconnectingSamePlayerReusesActiveSession() {
        InMemoryPlaySessionRepository repository = new InMemoryPlaySessionRepository();
        PlaySessionServiceImpl firstServiceInstance = new PlaySessionServiceImpl(repository);
        PlaySessionServiceImpl reconnectServiceInstance = new PlaySessionServiceImpl(repository);

        firstServiceInstance.beginSession("player-BPB2-123", "account-1", "game-1", "BPB2", "light");
        reconnectServiceInstance.beginSession("player-BPB2-123", "account-1", "game-1", "BPB2", "light");

        assertEquals(1, repository.count());

        reconnectServiceInstance.endSession("player-BPB2-123", 7);

        assertFalse(reconnectServiceInstance.isAliasActiveForOtherAccount("game-1", "BPB2", "account-2"));
    }

    @Test
    void formReentryWithStoredAccountMovesActiveSessionToNewPlayerId() {
        InMemoryPlaySessionRepository repository = new InMemoryPlaySessionRepository();
        PlaySessionServiceImpl firstServiceInstance = new PlaySessionServiceImpl(repository);
        PlaySessionServiceImpl reentryServiceInstance = new PlaySessionServiceImpl(repository);

        firstServiceInstance.beginSession("player-BPB2-old", "account-1", "game-1", "BPB2", "light");
        reentryServiceInstance.beginSession("player-BPB2-new", "account-1", "game-1", "BPB2", "light");

        assertEquals(1, repository.count());

        reentryServiceInstance.endSession("player-BPB2-new", 9);

        assertFalse(reentryServiceInstance.isAliasActiveForOtherAccount("game-1", "BPB2", "account-2"));
    }

    @Test
    void endsOnlyActiveSessionsForOneGame() {
        InMemoryPlaySessionRepository repository = new InMemoryPlaySessionRepository();
        PlaySessionServiceImpl service = new PlaySessionServiceImpl(repository);

        service.beginSession("player-BPB-old", "account-1", "game-1", "BPB", "light");
        service.beginSession("player-CDX-old", "account-2", "game-1", "CDX", "dark");
        service.beginSession("player-BPB-current", "account-1", "game-2", "BPB", "light");

        LocalDateTime endTime = LocalDateTime.now();
        service.endActiveSessionsForGame("game-1", endTime);

        assertFalse(service.isAliasActiveForOtherAccount("game-1", "BPB", "account-2"));
        assertFalse(service.isAliasActiveForOtherAccount("game-1", "CDX", "account-1"));
        assertTrue(service.isAliasActiveForOtherAccount("game-2", "BPB", "account-2"));
        assertEquals(endTime, repository.findActiveByPlayerId("player-BPB-old").orElseThrow().getEndTime());
        assertEquals(endTime, repository.findActiveByPlayerId("player-CDX-old").orElseThrow().getEndTime());
        assertNull(repository.findActiveByPlayerId("player-BPB-current").orElseThrow().getEndTime());
    }

    private static class InMemoryPlaySessionRepository implements PlaySessionRepository {
        private final Map<String, PlaySessionEntity> sessions = new LinkedHashMap<>();

        @Override
        public Optional<PlaySessionEntity> findActiveByGameAndAlias(String gameId, String alias) {
            return sessions.values().stream()
                    .filter(session -> gameId.equals(session.getGameId()))
                    .filter(session -> alias.equals(session.getAlias()))
                    .filter(session -> session.getEndTime() == null)
                    .findFirst();
        }

        Optional<PlaySessionEntity> findActiveByPlayerId(String playerId) {
            return sessions.values().stream()
                    .filter(session -> playerId.equals(session.getPlayerId()))
                    .findFirst();
        }

        @Override
        public int endActiveByGame(String gameId, LocalDateTime endTime) {
            int count = 0;
            for (PlaySessionEntity session : new ArrayList<>(sessions.values())) {
                if (gameId.equals(session.getGameId()) && session.getEndTime() == null) {
                    sessions.put(session.getId(), new PlaySessionEntity(
                            session.getId(),
                            session.getGameId(),
                            session.getAccountId(),
                            session.getPlayerId(),
                            session.getAlias(),
                            session.getTeam(),
                            session.getBeginTime(),
                            endTime,
                            session.getScore()
                    ));
                    count++;
                }
            }
            return count;
        }

        @Override
        public Optional<PlaySessionEntity> findById(String id) {
            return Optional.ofNullable(sessions.get(id));
        }

        @Override
        public List<PlaySessionEntity> findAll() {
            return new ArrayList<>(sessions.values());
        }

        @Override
        public PlaySessionEntity save(PlaySessionEntity entity) {
            sessions.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public boolean delete(PlaySessionEntity entity) {
            return entity != null && sessions.remove(entity.getId()) != null;
        }

        @Override
        public boolean deleteById(String id) {
            return sessions.remove(id) != null;
        }

        @Override
        public long count() {
            return sessions.size();
        }
    }
}
