package one.xis.seabattle;

public record GameStreamMessage(
        String type,
        GameSnapshot state
) {
}
