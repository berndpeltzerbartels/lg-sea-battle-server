package one.xis.seabattle.game;

/** Per-submarine memory only. All tactical decisions live in SubmarineBotCalculation. */
final class SubmarineBotState {
  // Dive/escape memory. Stopping or being blocked must never force surfacing.
  String divePhase = "surface";
  Vector2 lastAircraftPosition;
  double aircraftMemoryUntil;
}
