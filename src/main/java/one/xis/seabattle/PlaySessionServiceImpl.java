package one.xis.seabattle;

import one.xis.context.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
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
    public void beginSession(String playerId, String accountId, String gameId, String alias, String teamId) {
        if (playerId == null || playerId.isBlank()
                || accountId == null || accountId.isBlank()
                || gameId == null || gameId.isBlank()
                || alias == null || alias.isBlank()) {
            return;
        }
        if (sessionIdByPlayerId.containsKey(playerId)) {
            return;
        }
        Optional<PlaySessionEntity> activeSession = sessionRepository.findActiveByGameAndAlias(gameId, alias);
        if (activeSession.isPresent()) {
            PlaySessionEntity session = activeSession.get();
            if (accountId.equals(session.getAccountId())) {
                if (!playerId.equals(session.getPlayerId()) || !teamId.equals(session.getTeam())) {
                    session = new PlaySessionEntity(
                            session.getId(),
                            session.getGameId(),
                            session.getAccountId(),
                            playerId,
                            session.getAlias(),
                            teamId,
                            session.getBeginTime(),
                            session.getEndTime(),
                            session.getScore()
                    );
                    sessionRepository.save(session);
                }
                sessionIdByPlayerId.put(playerId, session.getId());
            }
            return;
        }
        String sessionId = UUID.randomUUID().toString();
        PlaySessionEntity session = new PlaySessionEntity(
                sessionId,
                gameId,
                accountId,
                playerId,
                alias,
                teamId,
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
                        session.getGameId(),
                        session.getAccountId(),
                        session.getPlayerId(),
                        session.getAlias(),
                        session.getTeam(),
                        session.getBeginTime(),
                        LocalDateTime.now(),
                        score
                ))
                .ifPresent(sessionRepository::save);
    }

    @Override
    public boolean isAliasActiveForOtherAccount(String gameId, String alias, String accountId) {
        if (gameId == null || gameId.isBlank() || alias == null || alias.isBlank()) {
            return false;
        }
        return sessionRepository.findActiveByGameAndAlias(gameId, alias)
                .filter(session -> accountId == null || !accountId.equals(session.getAccountId()))
                .isPresent();
    }
}
