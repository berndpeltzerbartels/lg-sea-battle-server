package one.xis.seabattle.game;

/** Per-submarine memory only. All tactical decisions live in SubmarineBotCalculation. */
final class SubmarineBotState {
  // Attack-distance hysteresis, independent of dive/escape memory.
  boolean holdingDistanceAstern;

  // Dive/escape and recovery memory.
  Vector2 progressPosition;
  double progressTime;
  double surfaceUntil;
  Vector2 surfaceRecoveryPosition;
  double surfaceRecoveryStarted;
  String divePhase = "surface";
  Vector2 lastAircraftPosition;
  double aircraftMemoryUntil;
}
