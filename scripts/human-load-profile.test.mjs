import test from 'node:test';
import assert from 'node:assert/strict';
import { cadence, navigation, observeNavigation, shotFor, stationAt } from './human-load-profile.mjs';

test('weapon rates follow client cooldowns independently of motion updates', () => {
  assert.equal(cadence.motion, .25);
  assert.equal(cadence.flak, .044);
  assert.equal(cadence.cannon, 1);
});
test('a player uses one station at a time and visits all firing stations', () => {
  assert.deepEqual([0, 16, 23, 30].map(t => stationAt(t, 0)), ['bridge', 'flak', 'cannon', 'bridge']);
});
const ship = { id: 's1', x: 10, z: 20, speed: 0, heading: 0 };
test('confirmed stationary position triggers reverse, turn, then forward without teleport', () => {
  const memory = {};
  navigation(ship, memory, 0);
  assert.equal(navigation(ship, memory, 5).engineOrder, 5);
  const reverse = navigation(ship, memory, 6);
  assert.equal(reverse.engineOrder, 0);
  assert.ok(reverse.speed < 0);
  assert.equal(reverse.debugTeleport, false);
  assert.ok(Math.hypot(reverse.x-ship.x, reverse.z-ship.z) < 1);
  assert.equal(reverse.rudderDegrees, 0);
  assert.equal(navigation(ship, memory, 12).engineOrder, 0);
  assert.equal(navigation({ ...ship, z: -5 }, memory, 18).engineOrder, 4);
  assert.equal(navigation({ ...ship, z: -5, heading: Math.PI / 2, speed: 2 }, memory, 20).engineOrder, 5);
});
test('recovery is detected off the bridge and does not expire just because time passes', () => {
  const memory = {};
  observeNavigation(ship, memory, 0);
  observeNavigation(ship, memory, 6);
  assert.equal(memory.recovery, 'reverse');
  const retry = navigation(ship, memory, 27);
  assert.equal(retry.engineOrder, 0);
  assert.equal(memory.attempts, 2);
  assert.notEqual(retry.rudderDegrees, 0);
});
test('retreat must be backwards, not sideways, and turning respects reverse steering', () => {
  const memory = {};
  navigation(ship, memory, 0);
  navigation(ship, memory, 6);
  assert.equal(navigation({ ...ship, x: 50 }, memory, 10).engineOrder, 0);
  const turning = navigation({ ...ship, z: -5, speed: -4 }, memory, 15);
  assert.equal(turning.engineOrder, 4);
  assert.ok(turning.turnVelocity < 0);
});
test('closed-loop recovery actually backs away and changes course before resuming stations', () => {
  const memory = {};
  let state = { ...ship };
  navigation(state, memory, 0);
  navigation(state, memory, 6);
  let minZ = state.z;
  for (let t = 6.25; t < 50 && memory.recovery; t += cadence.motion) {
    state = { ...state, ...navigation(state, memory, t) };
    minZ = Math.min(minZ, state.z);
  }
  assert.equal(memory.recovery, null);
  assert.ok(minZ < ship.z - 24);
  assert.ok(Math.abs(state.heading - Math.PI / 2) < .15);
});
test('moving ship and respawn do not trigger recovery', () => {
  const memory = {};
  navigation(ship, memory, 0);
  navigation({ ...ship, z: 30 }, memory, 6);
  assert.equal(memory.attempts, 0);
  navigation({ ...ship, id: 's2' }, memory, 15);
  assert.equal(memory.attempts, 0);
});
test('gun muzzle velocities use the client model scale and opposite safe directions', () => {
  const flak = shotFor(ship, 'flak', 0, 3);
  const cannon = shotFor(ship, 'cannon', 0, 3);
  assert.ok(flak.vz < 0);
  assert.ok(cannon.vz > 0);
  assert.ok(flak.z < ship.z);
  assert.ok(cannon.z > ship.z);
  assert.ok(Math.abs(Math.hypot(flak.vx, flak.vy, flak.vz) - 418 * 3) < .001);
});
