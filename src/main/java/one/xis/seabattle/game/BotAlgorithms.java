package one.xis.seabattle.game;

/**
 * The same implementation selection is used for startup, resets, and the in-memory lab. Configured
 * classes need a public no-argument constructor. No reflection runs during a tick.
 */
final class BotAlgorithms {
  private BotAlgorithms() {}

  static BotCalculation calculation() {
    return create("seaBattle.botCalculation", BotCalculation.class, StandardBotCalculation::new);
  }

  static BotTargetSelection targetSelection() {
    return create(
        "seaBattle.botTargetSelection", BotTargetSelection.class, StandardBotTargetSelection::new);
  }

  private static <T> T create(
      String property, Class<T> type, java.util.function.Supplier<T> fallback) {
    String name = System.getProperty(property);
    if (name == null || name.isBlank()) return fallback.get();
    try {
      return Class.forName(name).asSubclass(type).getConstructor().newInstance();
    } catch (ReflectiveOperationException | ClassCastException failure) {
      throw new IllegalArgumentException(
          "Cannot load "
              + property
              + "="
              + name
              + "; expected "
              + type.getName()
              + " with a public no-argument constructor",
          failure);
    }
  }
}
