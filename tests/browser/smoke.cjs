/* Run with externally installed Playwright; no frontend build/runtime is required. */
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const path = require("node:path");
let activeBrowser;
const base = process.env.REDNOTE_URL || "http://localhost:8085";
(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.REDNOTE_BROWSER_PATH ||
      "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
  });
  activeBrowser = browser;
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const p = await context.newPage(),
    errors = [],
    external = [];
  p.setDefaultTimeout(10000);
  p.on("pageerror", (e) => errors.push(e.message));
  p.on("request", (r) => {
    if (
      !r.url().startsWith(base) &&
      !r.url().startsWith("blob:") &&
      !r.url().startsWith("data:")
    )
      external.push(r.url());
  });
  const go = async (route) => {
    const response = await p.goto(base + route);
    assert.equal(response.status(), 200, route);
  };
  const login = async (username) => {
    await go("/login");
    await p.locator("#username").fill(username);
    await p.locator("#password").fill("Preview123");
    await Promise.all([
      p.waitForURL(base + "/"),
      p.locator("[data-auth] .primary").click(),
    ]);
  };
  const confirm = async () => p.locator('[role="dialog"] .primary').click();
  await go("/");
  assert.equal(await p.locator(".note-card").count(), 20);
  await p.locator("[data-load-feed]").evaluate((button) => button.click());
  await p.waitForFunction(
    () => document.querySelectorAll(".note-card").length >= 40,
  );
  await p.evaluate(() =>
    window.scrollTo(0, document.documentElement.scrollHeight),
  );
  await p.waitForFunction(
    () => document.querySelectorAll(".note-card").length === 44,
  );
  assert.equal(await p.locator("[data-load-feed]").isVisible(), false);
  console.log("feed loaded");
  await p.locator('[data-like="1"]').click();
  await p.locator('[role="dialog"]').waitFor();
  await confirm();
  await p.waitForURL(/\/login/);
  await p.locator("#username").fill("linxia");
  await p.locator("#password").fill("Preview123");
  await Promise.all([
    p.waitForURL(base + "/"),
    p.locator("[data-auth] .primary").click(),
  ]);
  assert.equal(
    await p.locator('[data-like="1"]').getAttribute("aria-pressed"),
    "false",
  );
  await p.locator('[data-like="1"]').click();
  await p.waitForFunction(
    () =>
      document.querySelector('[data-like="1"]').getAttribute("aria-pressed") ===
      "true",
  );
  console.log("login and like passed");
  await go("/notes/new");
  await p.locator("#title").fill("浏览器回归笔记");
  await p.locator("#body").fill("记录值得被看见的日常\n第二行正文");
  await p.route(
    "**/actions/files",
    (route) =>
      route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ message: "单张上传失败，请重试" }),
      }),
    { times: 1 },
  );
  await p
    .locator("#images")
    .setInputFiles(
      path.resolve(
        "docs/prototype/images/photo-1441974231531-c6227db76b6e.jpg",
      ),
    );
  await p
    .locator(".upload-item")
    .getByRole("button", { name: "重试" })
    .waitFor();
  await p.locator(".upload-item").getByRole("button", { name: "重试" }).click();
  await p.locator('input[name="images"]').waitFor({ state: "attached" });
  const generated = await p.evaluate(() => {
    const c = document.createElement("canvas");
    c.width = 2;
    c.height = 2;
    c.getContext("2d").fillRect(0, 0, 2, 2);
    return {
      png: c.toDataURL("image/png").split(",")[1],
      webp: c.toDataURL("image/webp").split(",")[1],
    };
  });
  await p.locator("#images").setInputFiles([
    {
      name: "tiny.png",
      mimeType: "image/png",
      buffer: Buffer.from(generated.png, "base64"),
    },
    {
      name: "tiny.webp",
      mimeType: "image/webp",
      buffer: Buffer.from(generated.webp, "base64"),
    },
  ]);
  await p.waitForFunction(
    () => document.querySelectorAll('input[name="images"]').length === 3,
  );
  await p
    .locator(".upload-item")
    .last()
    .getByRole("button", { name: "前移" })
    .click();
  await p
    .locator(".upload-item")
    .last()
    .getByRole("button", { name: "移除" })
    .click();
  await p.waitForFunction(
    () => document.querySelectorAll('input[name="images"]').length === 2,
  );
  await p.locator("[data-preview]").click();
  assert.equal(
    await p.locator('[role="dialog"] h3').textContent(),
    "浏览器回归笔记",
  );
  await p.locator('[role="dialog"] .btn').focus();
  await p.keyboard.press("Tab");
  assert.equal(
    await p
      .locator('[role="dialog"] .btn')
      .evaluate((el) => el === document.activeElement),
    true,
  );
  await p.keyboard.press("Escape");
  const failSave = (route) =>
    route.fulfill({
      status: 503,
      contentType: "application/json",
      body: JSON.stringify({ message: "回归模拟服务失败" }),
    });
  await p.route(base + "/notes", failSave);
  await p.locator("[data-editor] .primary").click();
  await p.getByText("回归模拟服务失败", { exact: true }).waitFor();
  assert.equal(await p.locator("#title").inputValue(), "浏览器回归笔记");
  assert.equal(await p.locator('input[name="images"]').count(), 2);
  await p.unroute(base + "/notes", failSave);
  await Promise.all([
    p.waitForURL(/\/notes\/\d+$/),
    p.locator("[data-editor] .primary").click(),
  ]);
  console.log("publish passed");
  const notePath = new URL(p.url()).pathname;
  assert.equal(await p.locator(".article h1").textContent(), "浏览器回归笔记");
  await p.locator("#comment-form textarea").fill("主评论 <script>");
  await p.locator("#comment-form .primary").click();
  await p.getByText("主评论 <script>", { exact: true }).waitFor();
  await p.locator("[data-reply]").first().click();
  await p.locator("#comment-form textarea").fill("第一条回复");
  await p.locator("#comment-form .primary").click();
  await p.waitForFunction(
    () => document.querySelector("#comment-form textarea").value === "",
  );
  await p.locator("[data-load-replies]").first().click();
  await p.getByText("第一条回复", { exact: true }).waitFor();
  assert.match(await p.locator("[data-comment-count]").textContent(), /2/);
  await p.getByRole("link", { name: "编辑", exact: true }).click();
  await p.locator("#title").fill("修改后的回归笔记");
  await p.locator("[data-editor] a.btn").click();
  await p.locator('[role="dialog"]').waitFor();
  await p.locator('[role="dialog"] .btn').first().click();
  assert.equal(await p.locator("#title").inputValue(), "修改后的回归笔记");
  await Promise.all([
    p.waitForURL(base + notePath),
    p.locator("[data-editor] .primary").click(),
  ]);
  assert.equal(
    await p.locator(".article h1").textContent(),
    "修改后的回归笔记",
  );
  await p.locator('[data-delete-comment][data-root-delete="true"]').click();
  await confirm();
  await p.waitForFunction(
    () =>
      document.querySelector("[data-comment-count]").textContent ===
      "共 0 条评论",
  );
  await p.route(
    base + "/actions" + notePath,
    (route) =>
      route.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({ message: "删除暂时失败，请重试" }),
      }),
    { times: 1 },
  );
  await p.locator("[data-delete-note]").click();
  await confirm();
  await p
    .locator('[role="dialog"] .error')
    .getByText("删除暂时失败，请重试", { exact: true })
    .waitFor();
  assert.equal(
    await p.locator(".article h1").textContent(),
    "修改后的回归笔记",
  );
  await p.keyboard.press("Escape");
  await p.locator("[data-delete-note]").click();
  await confirm();
  await p.waitForURL(base + "/");
  console.log("note comments/edit/delete passed");
  await go("/settings");
  await p
    .locator("#avatar-file")
    .setInputFiles({
      name: "avatar.png",
      mimeType: "image/png",
      buffer: Buffer.from(generated.png, "base64"),
    });
  await p.waitForFunction(
    () =>
      document.querySelector("[data-profile]").dataset.uploadPending ===
      "false",
  );
  await p.locator("#name").fill("林小夏回归");
  await Promise.all([
    p.waitForURL(base + "/users/1"),
    p.locator("[data-profile] .primary").click(),
  ]);
  assert.equal(await p.locator(".profile-head h1").textContent(), "林小夏回归");
  for (const width of [1440, 768, 375]) {
    await p.setViewportSize({ width, height: 1000 });
    for (const route of [
      "/",
      "/search?q=山",
      "/notes/1",
      "/notes/new",
      "/notes/1/edit",
      "/users/1",
      "/settings",
      "/login",
      "/register",
    ]) {
      await go(route);
      assert.equal(
        await p.locator(".avatar").evaluateAll((avatars) =>
          avatars.every((avatar) => {
            const r = avatar.getBoundingClientRect();
            return Math.abs(r.width - r.height) < 1;
          }),
        ),
        true,
        "avatar geometry " + width + " " + route,
      );
      if (process.env.REDNOTE_SCREENSHOTS)
        await p.screenshot({
          path: path.join(
            process.env.REDNOTE_SCREENSHOTS,
            route.replace(/[^a-zA-Z0-9]/g, "_") + "-" + width + ".png",
          ),
        });
      assert.equal(
        await p.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
        false,
        `${width} ${route}`,
      );
    }
    await go("/");
    if (process.env.REDNOTE_SCREENSHOTS)
      await p.screenshot({
        path: path.join(process.env.REDNOTE_SCREENSHOTS, `home-${width}.png`),
      });
  }
  await go("/settings");
  await p.locator("[data-logout] button").click();
  await confirm();
  await p.waitForURL(base + "/");
  await login("admin");
  for (const width of [1440, 768, 375]) {
    await p.setViewportSize({ width, height: 1000 });
    for (const route of ["/admin", "/admin/users"]) {
      await go(route);
      assert.equal(
        await p.locator(".avatar").evaluateAll((avatars) =>
          avatars.every((avatar) => {
            const r = avatar.getBoundingClientRect();
            return Math.abs(r.width - r.height) < 1;
          }),
        ),
        true,
        "avatar geometry " + width + " " + route,
      );
      if (process.env.REDNOTE_SCREENSHOTS)
        await p.screenshot({
          path: path.join(
            process.env.REDNOTE_SCREENSHOTS,
            route.replace(/[^a-zA-Z0-9]/g, "_") + "-" + width + ".png",
          ),
        });
      assert.equal(
        await p.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
        false,
        `${width} ${route}`,
      );
    }
  }
  await go("/admin");
  await p.route(
    "**/fragments/admin/dashboard",
    (route) =>
      route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ message: "统计刷新失败，请重试" }),
      }),
    { times: 1 },
  );
  await p.locator("[data-refresh]").click();
  await p.getByText("统计刷新失败，请重试", { exact: true }).first().waitFor();
  assert.equal(await p.locator(".metric").count(), 4);
  await p.locator("[data-refresh]").click();
  await p.waitForFunction(
    () => document.querySelector("[data-dashboard-error]").textContent === "",
  );
  await go("/admin/users");
  await p.locator('[data-user-detail="1"]').click();
  await p.locator('[role="dialog"] dl').waitFor();
  await p.keyboard.press("Escape");
  await p.locator('[data-user-status="1"]').click();
  await confirm();
  await p.waitForFunction(
    () =>
      document.querySelector('[data-user-status="1"]').dataset.status ===
      "正常",
  );
  await p.locator('[data-user-status="1"]').click();
  await confirm();
  await p.waitForFunction(
    () =>
      document.querySelector('[data-user-status="1"]').dataset.status ===
      "禁用",
  );
  const username = "test_" + Date.now().toString().slice(-10);
  await go("/register");
  await p.locator("#username").fill(username);
  await p.locator("#password").fill("Register123");
  await p.locator("#confirm").fill("Register123");
  await Promise.all([
    p.waitForURL(/\/login\?registered/),
    p.locator("[data-auth] .primary").click(),
  ]);
  await p.locator("#username").fill(username);
  await p.locator("#password").fill("Register123");
  await Promise.all([
    p.waitForURL(base + "/"),
    p.locator("[data-auth] .primary").click(),
  ]);
  await go("/settings");
  await p.locator('[data-tab="password"]').click();
  await p.locator("#old").fill("Register123");
  await p.locator("#new-password").fill("Changed123");
  await p.locator("#confirm").fill("Changed123");
  await Promise.all([
    p.waitForURL(/\/login\?passwordChanged/),
    p.locator("[data-password] .primary").click(),
  ]);
  await p.locator("#username").fill(username);
  await p.locator("#password").fill("Changed123");
  await Promise.all([
    p.waitForURL(base + "/"),
    p.locator("[data-auth] .primary").click(),
  ]);
  assert.deepEqual(errors, []);
  assert.deepEqual(external, []);
  console.log(
    "PASS: 11 pages × 3 viewports; feed, login return, upload, publish, edit, delete, comments/replies, settings, admin, register and password flows; no JS errors or external requests.",
  );
  await browser.close();
})().catch(async (e) => {
  console.error(e);
  await activeBrowser?.close();
  process.exit(1);
});
