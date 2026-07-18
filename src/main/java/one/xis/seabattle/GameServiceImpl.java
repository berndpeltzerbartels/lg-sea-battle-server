package one.xis.seabattle;

import one.xis.context.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
class GameServiceImpl implements GameService {

    private static final String ACTIVE = "ACTIVE";
    private static final String ENDED = "ENDED";

    private final GameRepository gameRepository;
    private final PlaySessionService playSessionService;
    private volatile GameEntity activeGame;

    GameServiceImpl(GameRepository gameRepository, PlaySessionService playSessionService) {
        this.gameRepository = gameRepository;
        this.playSessionService = playSessionService;
    }

    @Override
    public GameEntity activeGame() {
        GameEntity current = activeGame;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (activeGame == null) {
                activeGame = startNewGame();
            }
            return activeGame;
        }
    }

    private GameEntity startNewGame() {
        LocalDateTime now = LocalDateTime.now();
        gameRepository.findByStatus(ACTIVE).stream()
                .peek(game -> playSessionService.endActiveSessionsForGame(game.getId(), now))
                .map(game -> new GameEntity(game.getId(), ENDED, game.getBeginTime(), now))
                .forEach(gameRepository::save);
        return createGame(now);
    }

    private GameEntity createGame(LocalDateTime beginTime) {
        GameEntity game = new GameEntity(UUID.randomUUID().toString(), ACTIVE, beginTime, null);
        gameRepository.save(game);
        return game;
    }
}
