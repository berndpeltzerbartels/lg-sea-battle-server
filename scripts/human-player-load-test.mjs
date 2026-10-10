#!/usr/bin/env node
import { performance } from 'node:perf_hooks';
import { randomUUID } from 'node:crypto';
import { cadence, navigation, observeNavigation, shotFor, stationAt } from './human-load-profile.mjs';

const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const base = option('--base', 'http://127.0.0.1:9090').replace(/\/+$/, '');
const count = Number(option('--clients', 30));
const duration = Number(option('--duration', 180));
const noFlak = args.includes('--no-flak');
const fireAfter = Number(option('--fire-after', 0));
if (!Number.isFinite(fireAfter) || fireAfter < 0) throw Error('Invalid fire-after');
const aliasPrefix = option('--alias-prefix', null);
if (aliasPrefix && (!/^[A-Z0-9]+$/.test(aliasPrefix) || `${aliasPrefix}${count}`.length > 5)) {
  throw Error('Alias prefix plus player number must fit five uppercase letters/digits');
}
if (!Number.isInteger(count) || count < 1 || !Number.isFinite(duration) || duration <= 0) throw Error('Invalid clients/duration');
const metrics = new Map();
const clients = [];
let stopping = false;
process.on('SIGINT', () => { stopping = true; });
process.on('SIGTERM', () => { stopping = true; });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const seconds = () => performance.now() / 1000;

async function request(path, body, label = path) {
  const start = performance.now();
  const metric = metrics.get(label) ?? { times: [], statuses: {}, bytes: 0 };
  metrics.set(label, metric);
  let status = 0;
  try {
    const response = await fetch(base + path, { method: body === undefined ? 'GET' : 'POST',
      headers: body === undefined ? undefined : { 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(8000) });
    status = response.status;
    const text = await response.text();
    metric.bytes += Buffer.byteLength(text);
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { ok: response.ok, status, data };
  } finally {
    metric.times.push(performance.now() - start);
    metric.statuses[status] = (metric.statuses[status] ?? 0) + 1;
  }
}

function apply(client, message) {
  if (message.crew && (!client.crew || message.crew.revision >= client.crew.revision)) client.crew = message.crew;
  const state = message.state?.ships ? message.state : message;
  if (!state.ships || state.t < (client.lastStateTime ?? -1)) return;
  client.lastStateTime = state.t;
  client.ship = state.ships.find(ship => ship.controlledBy === client.playerId && ship.state === 'active');
  client.receivedAt = seconds();
  for (const shot of [...(state.weaponShots ?? []), ...(state.torpedoes ?? [])]) {
    if (shot.shipId === client.ship?.id) client.observed.add(shot.id);
  }
}

async function stream(client) {
  while (!stopping && !client.abort.signal.aborted) {
    try {
      const response = await fetch(`${base}/game/events/${encodeURIComponent(client.playerId)}`, { signal: client.abort.signal });
      if (!response.ok) throw Error(`SSE ${response.status}`);
      client.connections++;
      const decoder = new TextDecoder();
      let buffer = '';
      for await (const bytes of response.body) {
        client.bytes += bytes.length;
        buffer += decoder.decode(bytes, { stream: true }).replace(/\r/g, '');
        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          const data = frame.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trim()).join('\n');
          if (data) { apply(client, JSON.parse(data)); client.messages++; }
        }
      }
    } catch (error) {
      if (!client.abort.signal.aborted) { client.streamErrors++; console.error(`${client.alias}: ${error.message}`); }
    }
    if (!client.abort.signal.aborted) await sleep(1000);
  }
}

const command = (client, fields = {}) => ({ playerId: client.playerId, shipId: client.crew.shipId,
  revision: client.crew.revision, station: client.crew.station, ...fields });

async function play(client, started, scale) {
  let nextMotion = 0, nextShot = 0, motionPending = false;
  const pending = new Set();
  try {
    while (!stopping && seconds() - started < duration) {
      const now = seconds(), elapsed = now - started;
      if (!client.ship || !client.crew || now - client.receivedAt > 2) { await sleep(50); continue; }
      observeNavigation(client.ship, client.nav, elapsed);
      const station = client.nav.recovery ? 'bridge' : stationAt(elapsed, client.index);
      if (station !== client.crew.station) {
        await Promise.allSettled([...pending]);
        const response = await request('/game/crew/station', command(client, { station }), 'station');
        if (response.ok) {
          client.crew = response.data;
          nextShot = 0;
        }
        await sleep(20);
        continue;
      }
      if (now >= nextMotion && !motionPending) {
        nextMotion = now + cadence.motion;
        const motion = station === 'bridge' ? navigation(client.ship, client.nav, elapsed) : (() => {
          const shot = shotFor(client.ship, station, elapsed, scale);
          return { [`${station}Yaw`]: shot.weaponYaw, [`${station}Pitch`]: shot.weaponPitch };
        })();
        motionPending = true;
        const promise = request(`/game/crew/${station === 'bridge' ? 'motion' : 'aim'}`, command(client, { motion }), station === 'bridge' ? 'motion' : 'aim')
          .then(response => {
            if (response.ok) apply(client, response.data);
            // A kill can race a command; wait for the authoritative respawn snapshot.
            else if (response.status === 409) client.ship = null;
          }).catch(error => { client.errors++; console.error(error.message); })
          .finally(() => { motionPending = false; });
        pending.add(promise);
        promise.finally(() => pending.delete(promise));
      }
      if (elapsed >= fireAfter && seconds() >= nextShot && client.ship && !(noFlak && station === 'flak')) {
        const weapon = station === 'bridge' ? 'torpedo' : station;
        nextShot = seconds() + cadence[weapon];
        if (weapon !== 'torpedo' || client.ship.torpedoesRemaining > 0) {
          const fields = weapon === 'torpedo' ? { tubeSide: client.side *= -1 } : { shot: shotFor(client.ship, station, elapsed, scale) };
          // Like the browser, firing is not serialized behind previous shot responses.
          const promise = request(`/game/crew/fire-${weapon}`, command(client, fields), weapon)
            .then(response => { if (response.ok) apply(client, response.data); })
            .catch(error => { client.errors++; console.error(error.message); });
          pending.add(promise);
          promise.finally(() => pending.delete(promise));
        }
      }
      await sleep(4);
    }
  } finally { await Promise.allSettled([...pending]); }
}

try {
  const config = await request('/game/config');
  const scale = config.data?.vehicleScale?.torpedoBoat;
  if (!Number.isFinite(scale)) throw Error('Missing model scale');
  for (let index = 0; index < count && !stopping; index++) {
    const alias = aliasPrefix ? `${aliasPrefix}${index + 1}` : `T${randomUUID().replaceAll('-', '').slice(0, 4).toUpperCase()}`;
    const registered = await request('/game/start', { accountId: `loadtest-${randomUUID()}`, nickname: `Test ${index + 1}`,
      alias, team: index % 2 ? 'dark' : 'light', vehicleType: 'torpedo-boat', email: '' }, 'register');
    if (!registered.ok) throw Error(`Registration ${registered.status}`);
    const client = { index, alias, playerId: registered.data.player.playerId, nav: {}, side: 1,
      abort: new AbortController(), observed: new Set(), messages: 0, bytes: 0, connections: 0, streamErrors: 0, errors: 0 };
    clients.push(client);
    // These are one-time app loads, not additional recurring radar/state polling.
    for (const path of ['/game/world', '/game/version', `/game/session/${client.playerId}`, '/game/state']) {
      const response = await request(path);
      if (response.ok && path === '/game/state') apply(client, response.data);
    }
    const crew = await request(`/game/crew/${client.playerId}`, undefined, 'crew');
    if (!crew.data?.shipId) throw Error(`No crew for ${alias}`);
    client.crew = crew.data;
    client.stream = stream(client);
  }
  const started = seconds();
  if (fireAfter > 0) console.log(`PHASE: NO TEST-PLAYER FIRE for ${fireAfter}s; same players then enable weapons.`);
  console.log(`READY: ${clients.length} players, ${duration}s, ${base}; bridge/flak/cannon cycles, ${noFlak ? 'torpedoes and cannon only; NO FLAK FIRE' : 'all three weapons'}, no reset.`);
  const progress = setInterval(() => console.log(`Running ${Math.round(seconds() - started)}s: active=${clients.filter(c => c.ship).length}, recoveries=${clients.reduce((n,c) => n+(c.nav.attempts ?? 0),0)}, observed shots=${clients.reduce((n,c) => n+c.observed.size,0)}`), 30000);
  const fireNotice = fireAfter > 0 && fireAfter < duration
    ? setTimeout(() => console.log('PHASE: TEST-PLAYER FIRE ENABLED now.'), fireAfter * 1000) : null;
  try { await Promise.all(clients.map(client => play(client, started, scale))); }
  finally { clearInterval(progress); if (fireNotice) clearTimeout(fireNotice); }
} finally {
  stopping = true;
  for (const client of clients) client.abort.abort();
  await Promise.allSettled(clients.map(client => client.stream));
  await Promise.allSettled(clients.map(client => request('/game/crew/leave', { playerId: client.playerId }, 'leave')));
  console.log('FINISHED. endpoint count avg_ms p95_ms max_ms statuses');
  for (const [name, metric] of metrics) {
    const times = metric.times.sort((a,b) => a-b);
    console.log(name, times.length, (times.reduce((a,b) => a+b,0)/times.length).toFixed(1),
      times[Math.floor((times.length-1)*.95)]?.toFixed(1), times.at(-1)?.toFixed(1), JSON.stringify(metric.statuses));
  }
  console.log('SSE', JSON.stringify(clients.map(c => ({ alias:c.alias, messages:c.messages, errors:c.streamErrors,
    connections:c.connections, observedShots:c.observed.size, recoveries:c.nav.attempts ?? 0 }))));
}
