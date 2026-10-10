# Human-player server load profile

Run with `node scripts/human-player-load-test.mjs --base http://HOST --clients 30 --duration 180`.
This registers real temporary test accounts and affects the running battle. It does not reset
the world. It closes SSE connections and leaves the ships at the end, including on SIGINT.
Registered account records remain. `READY` marks the start of the measured interaction phase.
Add `--no-flak` to suppress only flak firing for comparison; station changes and aim updates remain unchanged.

The profile uses the current crew API, one SSE connection per player, independent players,
motion/aim every 250 ms with at most one such request in flight, flak every 44 ms,
cannon every second, and torpedoes every five seconds while on the bridge with ammunition.
Stations rotate with staggered phases: bridge 16 s, flak 7 s, cannon 7 s. No one fires
weapons assigned to another station. Firing requests are independent of movement requests.
SSE reconnects after failures; commands pause without a recent active-ship snapshot.
Ship death/respawn and crew revisions are followed from server snapshots.

If the confirmed ship position advances less than two units over six seconds at any station,
the simulated player takes the bridge and orders full astern. It stays there until the
server confirms 24 units of backwards displacement, then turns about 90 degrees at low
ahead speed. Station cycling resumes only after the turn and forward motion. Stalled
recovery retries with alternating rudder after 20 seconds reversing or 25 seconds turning.
Only normal motion commands are used; there is no teleport or game reset.

## Coverage and limits

This is HTTP/SSE server-load simulation, NOT Playwright and NOT a complete human substitute.
One-time world/version/session/state loads are included. Radar and crew data arrive in the
existing event stream; extra continuous state polling would exaggerate normal client traffic.
The output separately counts HTTP statuses and observed server projectile IDs: HTTP 200
alone does not establish a projectile was created (cooldowns can suppress firing).

Browser rendering, assets/cache behavior, sound, precise muzzle geometry and frame timing
are not benchmarked. The simple navigation model is not identical to client physics.
Client-reported aircraft intersections, optional `perf-log=1` telemetry, joining another crew,
warnings, depth charges, submarines and aircraft piloting are NOT part of this profile.
Do not claim this covers every game action or all client performance. Use real-browser
functional tests and additional action profiles for those flows. This profile represents
active solo torpedo-boat players switching between the three firing stations.

`409` remains visible in the output: a kill can race an in-flight command, but repeated
rejections on active ships indicate a test/API mismatch and invalidate the affected load.

Test the deterministic behavior with `node --test scripts/human-load-profile.test.mjs`.
