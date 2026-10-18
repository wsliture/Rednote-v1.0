/* 可选浏览器回归：需要外部提供 playwright；不向项目添加运行依赖。 */
const assert = require("node:assert/strict");
const { chromium } = require("playwright");
const base = process.env.REDNOTE_PROTOTYPE_URL || "http://localhost:8765";
(async () => {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.REDNOTE_BROWSER_PATH
      ? { executablePath: process.env.REDNOTE_BROWSER_PATH }
      : {}),
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const p = await context.newPage(),
    errors = [],
    external = [];
  p.on("pageerror", (e) => errors.push(e.message));
  await p.route("**/*", (r) => {
    if (!r.request().url().startsWith(base)) {
      external.push(r.request().url());
      return r.abort();
    }
    return r.continue();
  });
  const wait = async (page) => {
    await p.waitForFunction(
      (name) => document.body.dataset.page === name,
      page,
    );
    await p.waitForTimeout(70);
  };
  const go = async (route, page) => {
    await p.evaluate((r) => (location.hash = r), route);
    await wait(page);
  };
  const persona = async (id, state = "normal") => {
    await p.locator("[data-demo]").click();
    await p.locator("#demo-role").selectOption(String(id));
    await p.locator("#demo-state").selectOption(state);
    await p.locator("#apply-demo").click();
    await p.waitForTimeout(70);
  };
  const confirm = async () => {
    await p.locator("#confirm-ok").click();
    await p.waitForTimeout(90);
  };
  try {
    await p.goto(base);
    await wait("index");
    assert.equal(await p.locator(".note-card").count(), 20);
    const before = await p
      .locator('[data-like="1"] [data-count]')
      .textContent();
    await p.locator('[data-like="1"]').click();
    await wait("login");
    await p.locator("#username").fill("linxia");
    await p.locator("#password").fill("Demo1234");
    await p.locator("#auth-form .primary").click();
    await wait("index");
    assert.equal(
      await p.locator('[data-like="1"] [data-count]').textContent(),
      before,
      "登录不自动补发点赞",
    );
    await p.locator('[data-like="1"]').click();
    assert.equal(
      Number(await p.locator('[data-like="1"] [data-count]').textContent()),
      Number(before) + 1,
    );
    await p.locator("#search-form input").fill("生活");
    await p.locator("#search-form").evaluate((f) => f.requestSubmit());
    await wait("search");
    assert.ok((await p.locator(".note-card").count()) > 0);
    await go("/note/1", "note");
    await p.locator('[data-slide="1"]').click();
    assert.equal(await p.locator(".counter").textContent(), "2 / 2");
    await p.locator("#comment-form textarea").fill("重构回归：主评论");
    await p.locator("#comment-form .primary").click();
    assert.match(await p.locator(".comments").innerText(), /重构回归：主评论/);
    await p.locator("[data-reply]").first().click();
    await p.locator("#comment-form textarea").fill("回复仍为两层");
    await p.locator("#comment-form .primary").click();
    assert.match(await p.locator(".reply").first().innerText(), /回复仍为两层/);
    await p.locator("[data-delete-comment]").first().click();
    assert.match(await p.locator(".modal p").innerText(), /所有回复/);
    await confirm();
    assert.doesNotMatch(
      await p.locator(".comments").innerText(),
      /重构回归：主评论/,
    );
    await go("/publish", "publish");
    await p.locator("#title").fill("重构测试笔记");
    await p.locator("#body").fill("发布后可编辑和删除");
    await persona(1, "upload-error");
    await p
      .locator("#images")
      .setInputFiles(
        "docs/prototype/images/photo-1441974231531-c6227db76b6e.jpg",
      );
    await p.waitForFunction(
      () => document.querySelectorAll(".upload-tile").length === 1,
    );
    assert.equal(await p.locator("[data-retry-image]").isVisible(), true);
    await p.locator("#publish-button").click();
    assert.match(
      await p.locator('[data-error="images"]').textContent(),
      /上传失败/,
    );
    await p.locator("[data-retry-image]").click();
    await persona(1, "unknown");
    await p.locator("#publish-button").click();
    assert.match(await p.locator("#publish-error").textContent(), /结果未知/);
    assert.equal(await p.locator("#title").inputValue(), "重构测试笔记");
    await persona(1);
    await p
      .locator("#images")
      .setInputFiles(
        "docs/prototype/images/photo-1495474472287-4d71bcdd2085.jpg",
      );
    await p.waitForFunction(
      () => document.querySelectorAll(".upload-tile").length === 2,
    );
    const firstImage = await p
      .locator(".upload-tile img")
      .first()
      .getAttribute("src");
    await p.locator('[data-move="1"][data-dir="-1"]').click();
    assert.notEqual(
      await p.locator(".upload-tile img").first().getAttribute("src"),
      firstImage,
    );
    await p.locator('[data-remove-image="1"]').click();
    await p.locator('.sidebar .navitem[href="#/"]').click();
    await p.locator("[data-close]").last().click();
    assert.equal(await p.locator("#title").inputValue(), "重构测试笔记");
    await p.locator("[data-preview]").click();
    assert.equal(await p.locator(".modal h2").textContent(), "重构测试笔记");
    await p.locator("[data-close]").click();
    await p.locator("#publish-button").click();
    await wait("note");
    assert.equal(await p.locator(".article h1").textContent(), "重构测试笔记");
    await p.locator('[data-bind="owner-actions"] a').click();
    await wait("edit");
    await p.locator("#title").fill("编辑成功");
    await p.locator("#publish-button").click();
    await wait("note");
    assert.equal(await p.locator(".article h1").textContent(), "编辑成功");
    await p.locator("[data-delete-note]").click();
    await confirm();
    await wait("profile");
    assert.doesNotMatch(await p.locator(".content").innerText(), /编辑成功/);
    await go("/note/999999", "note");
    assert.equal(
      await p.locator(".empty h2").textContent(),
      "笔记不存在或已删除",
    );
    await go("/settings", "settings");
    await p.locator("#name").fill("回归昵称");
    await p.locator("#settings-form .primary").click();
    assert.equal(await p.locator("#name").inputValue(), "回归昵称");
    await p.locator("[data-settings=password]").click();
    await p.locator("#old").fill("Demo1234");
    await p.locator("#password").fill("Changed123");
    await p.locator("#confirm").fill("Changed123");
    await p.locator("#settings-form .primary").click();
    await wait("login");
    await p.locator("#username").fill("linxia");
    await p.locator("#password").fill("Changed123");
    await p.locator("#auth-form .primary").click();
    await wait("index");
    await go("/register", "register");
    await p.locator("#username").fill("new_demo");
    await p.locator("#password").fill("Signup123");
    await p.locator("#confirm").fill("Signup123");
    await p.locator("#auth-form .primary").click();
    await wait("login");
    await p.locator("#username").fill("new_demo");
    await p.locator("#password").fill("Signup123");
    await p.locator("#auth-form .primary").click();
    await wait("index");
    await go("/admin", "admin");
    assert.equal(await p.locator(".empty h2").textContent(), "仅管理员可访问");
    await persona(4);
    assert.equal(await p.locator(".metric").count(), 4);
    await go("/admin/users", "admin-users");
    await p.locator("#fu").fill("linxia");
    await p.locator("#filter-form .primary").click();
    assert.equal(await p.locator("tbody tr").count(), 1);
    await p.locator('[data-user-detail="1"]').click();
    assert.equal(
      await p.locator(".drawer [data-name]").textContent(),
      "回归昵称",
    );
    await p.locator("[data-close]").click();
    await p.locator('[data-status-user="1"]').click();
    await confirm();
    assert.equal(await p.locator("tbody .status").textContent(), "禁用");
    await p.locator('[data-status-user="1"]').click();
    await confirm();
    assert.equal(await p.locator("tbody .status").textContent(), "正常");
    await go("/", "index");
    for (const state of ["loading", "empty", "error"]) {
      await persona(4, state);
      assert.ok(
        (await p
          .locator(state === "loading" ? ".skeleton" : ".empty")
          .count()) > 0,
      );
    }
    await persona(4);
    await go("/publish", "publish");
    await p.locator("#title").fill("保留草稿");
    await persona(4, "error");
    assert.equal(await p.locator("#title").inputValue(), "保留草稿");
    await persona(4, "expired");
    assert.equal(await p.locator("#title").inputValue(), "保留草稿");
    await persona(4);
    await p.locator("[data-cancel-editor]").click();
    await confirm();
    await wait("index");
    // 冷加载失败保留原页面；重试后再快速导航，验证只挂载最终页面。
    const fresh = await context.newPage();
    await fresh.goto(base);
    await fresh.waitForFunction(
      () => document.querySelectorAll(".note-card").length === 20,
    );
    await fresh.route("**/search.html", (r) => r.abort());
    await fresh.locator("#search-form input").fill("咖啡");
    await fresh.locator("#search-form").evaluate((f) => f.requestSubmit());
    await fresh.locator("#confirm-ok").waitFor();
    assert.equal(await fresh.locator(".note-card").count(), 20);
    await fresh.unroute("**/search.html");
    await fresh.locator("#confirm-ok").click();
    await fresh.waitForFunction(() => document.body.dataset.page === "search");
    await fresh.evaluate(() => {
      location.hash = "/note/1";
      setTimeout(() => (location.hash = "/register"), 5);
    });
    await fresh.waitForFunction(
      () => document.body.dataset.page === "register",
    );
    await fresh.waitForTimeout(100);
    assert.equal(await fresh.locator("#confirm").count(), 1);
    await fresh.goBack();
    await fresh.waitForTimeout(100);
    await fresh.goForward();
    await fresh.waitForFunction(
      () => document.body.dataset.page === "register",
    );
    await fresh.evaluate(() => {
      const data = JSON.parse(localStorage.getItem("rednote-prototype-v1"));
      data.notes[0].title = "旧数据保留";
      data.notes[0].images[0] =
        "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=900&q=85";
      localStorage.setItem("rednote-prototype-v1", JSON.stringify(data));
    });
    await fresh.reload();
    const legacy = await fresh.evaluate(
      () => JSON.parse(localStorage.getItem("rednote-prototype-v1")).notes[0],
    );
    assert.equal(legacy.title, "旧数据保留");
    assert.equal(
      legacy.images[0],
      "images/photo-1441974231531-c6227db76b6e.jpg",
    );
    await fresh.close();
    const direct = {
      index: "/",
      search: "/search",
      note: "/note/1",
      publish: "/publish",
      edit: "/edit/1",
      login: "/login",
      register: "/register",
      profile: "/profile/1",
      settings: "/settings",
      admin: "/admin",
      "admin-users": "/admin/users",
    };
    for (const [name] of Object.entries(direct)) {
      await p.goto(`${base}/${name}.html`);
      await wait(name);
      for (const width of [1440, 768, 375]) {
        await p.setViewportSize({ width, height: 1000 });
        assert.equal(
          await p.evaluate(
            () => document.documentElement.scrollWidth > innerWidth,
          ),
          false,
          `${name} ${width} 横向溢出`,
        );
      }
    }
    assert.deepEqual(errors, []);
    assert.deepEqual(external, []);
    console.log(
      "PASS: 核心业务流程、独立页面、异常状态、导航与 33 个响应式场景；无脚本错误或外部请求。",
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
