package one.xis.seabattle.game;

public record GameStreamMessage(
        String type,
        GameSnapshot state
) {
}
