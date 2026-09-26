import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(new URL("../../client/package.json", import.meta.url));
const { chromium } = require("playwright");
const base = process.env.CREW_TEST_URL ?? "http://127.0.0.1:9092";
const browser = await chromium.launch({ headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1100, height: 800 } });
  const page = await context.newPage();
  const scenario = await page.request.post(`${base}/game/test-scenario`, { data: {
    adminKey: "bernd", scenario: `scenario: crew-smoke
version: 9300
cell: 1000
map:
.........
.1.2.3.4.
.........
objects:
1: ship light bot [speed: 0knt]
2: ship light bot [speed: 0knt]
3: ship light bot [speed: 0knt]
4: ship light bot [speed: 0knt]`
  }});
  assert.equal(scenario.status(), 200);
  const errors = [];
  let eventStream = false;
  page.on("pageerror", error => errors.push(error.message));
  page.on("response", response => {
    if (response.headers()["content-type"]?.includes("text/event-stream")) eventStream = true;
  });
  const alias = `A${Date.now().toString(36).slice(-4)}`.toUpperCase();
  await page.goto(`${base}/start.html`);
  await page.locator("#nickname").fill("Crew Test");
  await page.locator("#alias").fill(alias);
  await page.locator("#team").selectOption("light");
  await page.locator("#vehicleType").selectOption("crew");
  await page.locator('button[xis\\:action="startGame"]').click();
  await page.waitForURL("**/crew.html");
  await page.locator("h1").filter({ hasText: "Anheuern" }).waitFor();
  const initialState = await (await page.request.get(`${base}/game/state`)).json();
  assert.ok(initialState.ships.every(s => !s.controlledBy?.startsWith(`player-${alias}-`)));

  // Another client registers while the applicant stays on the open XIS page.
  const captain = await browser.newContext();
  const captainAccount = `crew-smoke-${Date.now()}`;
  const registration = await captain.request.post(`${base}/game/start`, { data: {
    accountId: captainAccount, nickname: "Captain Test", alias: `C${alias.slice(1)}`,
    team: "light", vehicleType: "torpedo-boat"
  }});
  assert.equal(registration.status(), 200);
  const login = await registration.json();
  const bridge = await captain.newPage();
  bridge.on("pageerror", error => errors.push(error.message));
  await bridge.addInitScript(id => localStorage.setItem("accountId", JSON.stringify({ value: id })), captainAccount);
  await bridge.goto(`${base}/app?scenarioTest=1`);
  const inbox = bridge.frameLocator('iframe[title="Besatzungsanfragen"]');
  await bridge.locator('iframe[title="Besatzungsanfragen"]').waitFor({ state: "attached", timeout: 15000 }).catch(error => {
    console.log({ errors, url: bridge.url() });
    throw error;
  });
  assert.equal(await bridge.locator('iframe[title="Besatzungsanfragen"]').isVisible(), false);
  const state = await (await captain.request.get(`${base}/game/state`)).json();
  const ship = state.ships.find(s => s.controlledBy === login.player.playerId);
  assert.ok(ship);
  try {
    await page.waitForFunction(id => [...document.querySelectorAll("#shipId option")].some(o => o.value === id), ship.id, { timeout: 10000 });
  } catch (error) {
    console.log({ eventStream, errors, body: await page.locator("body").innerText(), ship: ship.id });
    throw error;
  }
  assert.ok(eventStream, "XIS SSE connection must be active");
  await page.locator("#shipId").selectOption(ship.id);
  await page.locator('button[xis\\:action="request"]').click();
  await page.locator("li").filter({ hasText: `${ship.id}: Offen` }).waitFor();
  await page.reload();
  await page.locator("li").filter({ hasText: `${ship.id}: Offen` }).waitFor();
  assert.equal(await page.locator(`#shipId option[value="${ship.id}"]`).count(), 0);
  await page.screenshot({ path: "/tmp/crew-page-desktop.png" });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: "/tmp/crew-page-mobile.png" });
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
  await inbox.getByRole("button", { name: "Annehmen" }).waitFor();
  assert.ok(await bridge.evaluate(() => document.activeElement?.tagName !== "IFRAME"));
  await bridge.screenshot({ path: "/tmp/crew-inbox-desktop.png" });
  await bridge.setViewportSize({ width: 390, height: 844 });
  await bridge.screenshot({ path: "/tmp/crew-inbox-mobile.png" });
  await inbox.getByRole("button", { name: "Annehmen" }).click();
  await page.waitForURL("**/app?vehicle=torpedo-boat", { timeout: 10000 }).catch(async error => {
    console.log({ errors, url: page.url(), body: await page.locator("body").innerText(), scripts: await page.locator("script").allTextContents() });
    throw error;
  });
  await page.waitForFunction(() => document.body.dataset.crewStation === "flak");
  assert.equal(await page.getAttribute("body", "data-player-ship-id"), ship.id);
  await bridge.waitForFunction(() => document.body.dataset.crewMembers === "2");
  assert.ok(await bridge.locator("#flakViewButton").isDisabled());
  assert.ok(await page.locator("#bridgeViewButton").isDisabled());
  await page.screenshot({ path: "/tmp/crew-gunner.png" });
  await bridge.locator('iframe[title="Besatzungsanfragen"]').waitFor({ state: "hidden" });
  assert.ok(await bridge.evaluate(() => document.activeElement?.tagName !== "IFRAME"), "Decision must release keyboard focus");
  const rejectedContext = await browser.newContext();
  const rejected = await rejectedContext.newPage();
  rejected.on("pageerror", error => errors.push(error.message));
  await rejected.goto(`${base}/start.html`);
  await rejected.locator("#nickname").fill("Second Applicant");
  await rejected.locator("#alias").fill(`R${alias.slice(1)}`);
  await rejected.locator("#team").selectOption("light");
  await rejected.locator("#vehicleType").selectOption("crew");
  await rejected.locator('button[xis\\:action="startGame"]').click();
  await rejected.waitForURL("**/crew.html");
  await rejected.locator("#shipId").selectOption(ship.id);
  await rejected.locator('button[xis\\:action="request"]').click();
  await inbox.getByRole("button", { name: "Ablehnen" }).click();
  await rejected.locator("li").filter({ hasText: `${ship.id}: Abgelehnt` }).waitFor();
  assert.equal(await rejected.locator(`#shipId option[value="${ship.id}"]`).count(), 0);
  const other = await captain.request.post(`${base}/game/start`, { data: {
    accountId: `crew-other-${Date.now()}`, nickname: "Other Captain", alias: `D${alias.slice(1)}`,
    team: "light", vehicleType: "torpedo-boat"
  }});
  assert.equal(other.status(), 200);
  const otherLogin = await other.json();
  const otherState = await (await captain.request.get(`${base}/game/state`)).json();
  const otherShip = otherState.ships.find(s => s.controlledBy === otherLogin.player.playerId);
  await rejected.waitForFunction(id => [...document.querySelectorAll("#shipId option")].some(o => o.value === id), otherShip.id);
  await rejected.reload();
  await rejected.locator("li").filter({ hasText: `${ship.id}: Abgelehnt` }).waitFor();
  assert.equal(await rejected.locator(`#shipId option[value="${ship.id}"]`).count(), 0);
  await rejected.locator("#shipId").selectOption(otherShip.id);
  await rejected.locator('button[xis\\:action="request"]').click();
  await rejected.locator("li").filter({ hasText: `${otherShip.id}: Offen` }).waitFor();
  await bridge.setViewportSize({ width: 1280, height: 900 });
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(`${base}/app?vehicle=torpedo-boat&scenarioTest=1`);
  await page.waitForFunction(() => document.body.dataset.crewStation === "flak");
  await page.locator("#cannonViewButton").click();
  await page.waitForFunction(() => document.body.dataset.crewStation === "cannon");
  await bridge.waitForFunction(() => document.querySelector("#cannonViewButton").disabled);
  await bridge.evaluate(() => {
    window.aimSamples = [];
    function sample() {
      window.aimSamples.push(window.seaBattleScenarioTest.cannonAimDisplay());
      if (window.aimSamples.length < 180) requestAnimationFrame(sample);
    }
    requestAnimationFrame(sample);
  });
  await page.evaluate(() => window.seaBattleScenarioTest.cannonShotLineAt({ yaw: 1, pitch: 0.2 }));
  assert.equal(await page.evaluate(() => window.seaBattleScenarioTest.cannonAimDisplay().yaw), 1);
  await bridge.waitForFunction(() => {
    const aim = window.seaBattleScenarioTest.cannonAimDisplay();
    return Math.abs(aim.targetYaw - 1) < 0.001 && Math.abs(aim.yaw - 1) < 0.02;
  });
  assert.ok(await bridge.evaluate(() => window.aimSamples.some(aim =>
    Math.abs(aim.targetYaw - 1) < 0.001 && aim.yaw > 0.01 && aim.yaw < 0.95)));
  const target = { x: ship.x, y: 40, z: ship.z + 1000 };
  async function checkSharedCannonSmoke(count) {
    await page.waitForFunction(() => document.body.dataset.cannonReloading !== "true");
    const fired = await page.evaluate(target => window.seaBattleScenarioTest.fireCannonAt(target), target);
    assert.equal(fired.fire, "ok");
    await bridge.waitForFunction(count => {
      const fx = window.seaBattleScenarioTest.cannonEffects();
      return fx.blasts === count && fx.smoke === 4;
    }, count);
    assert.equal(await page.evaluate(() => window.seaBattleScenarioTest.cannonEffects().blasts), count);
    await bridge.screenshot({ path: `/tmp/crew-cannon-smoke-${count}.png` });
  }
  await checkSharedCannonSmoke(1);
  await bridge.locator("#flakViewButton").click();
  await bridge.waitForFunction(() => document.body.dataset.crewStation === "flak");
  await bridge.evaluate(target => window.seaBattleScenarioTest.aimFlakAt(target), target);
  await checkSharedCannonSmoke(2);
  await page.waitForFunction(() => !document.querySelector("#bridgeViewButton").disabled);
  await page.locator("#bridgeViewButton").click();
  await page.waitForFunction(() => document.body.dataset.crewStation === "bridge");
  await page.locator("#torpedoAidButton").click();
  assert.equal(await page.getAttribute("body", "data-crew-station"), "bridge");
  await page.locator("#bridgeViewButton").click();
  await page.keyboard.press("ArrowUp");
  await page.waitForFunction(() => document.querySelector("#telegraphOrderValue").textContent.trim().toLowerCase() !== "stop");
  await bridge.screenshot({ path: "/tmp/crew-two-players.png" });
  await bridge.locator(".crew-leave").click();
  await bridge.waitForURL("**/start.html");
  await page.waitForFunction(() => document.body.dataset.crewMembers === "1");
  assert.equal(await page.getAttribute("body", "data-player-ship-id"), ship.id);
  await page.reload();
  await page.waitForFunction(() => document.body.dataset.crewStation === "bridge");
  assert.equal(await page.getAttribute("body", "data-player-ship-id"), ship.id);
  assert.deepEqual(errors, []);
  console.log("PASS: XIS recruitment, shared cannon smoke on bridge and flak, exclusive stations, bridge/torpedo view, driver handover, leave and reconnect");
} finally {
  await browser.close();
}
