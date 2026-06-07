package one.xis.seabattle;

import one.xis.context.Service;

@Service
public class GameStateService {

    private final GameSession session = new GameSession();

    public GameSnapshot snapshot() {
        return session.snapshot();
    }
}
