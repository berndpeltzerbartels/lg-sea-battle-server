package one.xis.seabattle;

import one.xis.context.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
class PlaySessionServiceImpl implements PlaySessionService {

    private final PlaySessionRepository sessionRepository;
    private final Map<String, String> sessionIdByPlayerId = new ConcurrentHashMap<>();

    PlaySessionServiceImpl(PlaySessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void beginSession(String playerId, String accountId) {
        if (playerId == null || playerId.isBlank() || accountId == null || accountId.isBlank()) {
            return;
        }
        if (sessionIdByPlayerId.containsKey(playerId)) {
            return;
        }
        String sessionId = UUID.randomUUID().toString();
        PlaySessionEntity session = new PlaySessionEntity(
                sessionId,
                accountId,
                playerId,
                LocalDateTime.now(),
                null,
                0
        );
        sessionRepository.save(session);
        sessionIdByPlayerId.put(playerId, sessionId);
    }

    @Override
    public void endSession(String playerId, int score) {
        String sessionId = sessionIdByPlayerId.remove(playerId);
        if (sessionId == null) {
            return;
        }
        sessionRepository.findById(sessionId)
                .map(session -> new PlaySessionEntity(
                        session.getId(),
                        session.getAccountId(),
                        session.getPlayerId(),
                        session.getBeginTime(),
                        LocalDateTime.now(),
                        score
                ))
                .ifPresent(sessionRepository::save);
    }
}
