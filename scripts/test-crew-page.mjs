import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(new URL("../../client/package.json", import.meta.url));
const { chromium } = require("playwright");
const base = process.env.CREW_TEST_URL ?? "http://127.0.0.1:9092";
const browser = await chromium.launch({ headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1100, height: 800 } });
  const page = await context.newPage();
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
  await bridge.goto(`${base}/app`);
  const inbox = bridge.frameLocator('iframe[title="Besatzungsanfragen"]');
  await bridge.locator('iframe[title="Besatzungsanfragen"]').waitFor({ state: "attached" });
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
  await page.locator("li").filter({ hasText: `${ship.id}: Angenommen` }).waitFor();
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
  assert.deepEqual(errors, []);
  console.log("PASS: XIS login, SSE, acceptance, focus, rejection, retry protection and applying to another ship");
} finally {
  await browser.close();
}
