/* Focused regression: comment paging/empty states and sidebar/composer geometry. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");
const base = process.env.REDNOTE_URL || "http://localhost:8086";
let browser;
let cleanup;
(async () => {
  browser = await chromium.launch({
    headless: true,
    executablePath:
      process.env.REDNOTE_BROWSER_PATH ||
      "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
  });
  const page = await browser.newPage({
      viewport: { width: 1440, height: 1000 },
    }),
    errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(base + "/login");
  await page.locator("#username").fill("linxia");
  await page.locator("#password").fill("Preview123");
  await Promise.all([
    page.waitForURL(base + "/"),
    page.locator("[data-auth] .primary").click(),
  ]);
  const csrf = await page
    .locator('meta[name="csrf-token"]')
    .getAttribute("content");
  const headers = { "X-CSRF-TOKEN": csrf };
  const upload = await page.request.post(base + "/actions/files", {
    headers,
    multipart: {
      file: {
        name: "layout.jpg",
        mimeType: "image/jpeg",
        buffer: fs.readFileSync(
          path.resolve(
            "docs/prototype/images/photo-1449247709967-d4461a6a6103.jpg",
          ),
        ),
      },
    },
  });
  assert.equal(upload.status(), 200);
  const file = await upload.json();
  const created = await page.request.post(base + "/notes", {
    headers,
    form: {
      title: "评论区排版验收",
      body: "留一点空间，让交流更轻松。",
      images: file.id,
    },
  });
  assert.equal(created.status(), 200);
  const notePath = new URL(created.url()).pathname,
    noteId = notePath.split("/").at(-1);
  cleanup = () =>
    page.request.delete(base + "/actions/notes/" + noteId, { headers });
  const comment = async (text, target) => {
    const response = await page.request.post(
      base + "/actions/notes/" + noteId + "/comments",
      { headers, data: { text, target } },
    );
    assert.equal(response.status(), 200);
    return response.json();
  };
  const more = page.locator("[data-more-comments]");
  await page.goto(base + notePath);
  assert.equal(await page.locator("[data-comments-empty]:visible").count(), 1);
  assert.equal(await more.isVisible(), false);
  for (const width of [1440, 768, 375]) {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto(base + notePath);
    assert.equal(
      await page.locator("[data-comments-empty]:visible").count(),
      1,
    );
    const geometry = await page.evaluate(() => {
      const rect = (selector) =>
        document.querySelector(selector).getBoundingClientRect();
      const labels = [...document.querySelectorAll(".sidebar .nav-label")].map(
        (el) => el.getBoundingClientRect().left,
      );
      const label = rect(".composer-label"),
        input = rect("#comment-form textarea"),
        actions = rect(".composer-actions"),
        comments = rect("[data-comments]"),
        composer = rect(".composer");
      return {
        labels,
        labelGap: input.top - label.bottom,
        actionsGap: actions.top - input.bottom,
        composerGap: composer.top - comments.bottom,
        overflow: document.documentElement.scrollWidth > innerWidth,
      };
    });
    assert.equal(geometry.overflow, false);
    assert.ok(Math.abs(geometry.labelGap - 12) < 1);
    assert.ok(Math.abs(geometry.actionsGap - 12) < 1);
    assert.ok(Math.abs(geometry.composerGap - (width <= 600 ? 20 : 24)) < 1);
    if (width > 600) {
      assert.ok(
        Math.max(...geometry.labels) - Math.min(...geometry.labels) < 1,
      );
      const before = await page
        .locator(".nav-label")
        .first()
        .evaluate((el) => el.getBoundingClientRect().left);
      await page.locator(".sidebar .navitem").first().hover();
      assert.equal(
        await page
          .locator(".nav-label")
          .first()
          .evaluate((el) => el.getBoundingClientRect().left),
        before,
      );
    }
    if (process.env.REDNOTE_SCREENSHOTS) {
      fs.mkdirSync(process.env.REDNOTE_SCREENSHOTS, { recursive: true });
      await page.screenshot({
        path: path.join(
          process.env.REDNOTE_SCREENSHOTS,
          `comments-empty-${width}.png`,
        ),
        fullPage: true,
      });
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(base + notePath);
  await page.locator("#comment-form textarea").fill("唯一评论");
  await page.locator("#comment-form .primary").click();
  await page.getByText("唯一评论", { exact: true }).waitFor();
  assert.equal(await page.locator("[data-comments-empty]:visible").count(), 0);
  assert.equal(await more.isVisible(), false);
  await page.locator("[data-delete-comment]").click();
  await page.locator('[role="dialog"] .primary').click();
  await page.locator("[data-comments-empty]:visible").waitFor();
  for (let i = 0; i < 3; i++) {
    await page.reload();
    assert.equal(
      await page.locator("[data-comments-empty]:visible").count(),
      1,
    );
    assert.equal(await more.isVisible(), false);
  }
  const roots = [];
  for (let i = 0; i < 20; i++) roots.push(await comment("分页评论 " + i));
  await page.reload();
  assert.equal(
    await page
      .locator("[data-comment-list] > .comment-batch > .comment")
      .count(),
    20,
  );
  assert.equal(await more.isVisible(), false);
  roots.push(await comment("第 21 条主评论 · " + "长文本".repeat(80)));
  await page.reload();
  assert.equal(await more.isVisible(), true);
  await more.click();
  await page.waitForFunction(
    () =>
      document.querySelectorAll(
        "[data-comment-list] > .comment-batch > .comment",
      ).length === 21,
  );
  assert.equal(await more.isVisible(), false);
  assert.equal(await page.locator("[data-comments-empty]:visible").count(), 0);
  assert.equal(
    await page.getByText("暂无更多评论，留下你的想法吧。").count(),
    0,
  );
  // Repeated synthetic clicks on a hidden end-of-list control must not issue more requests.
  let requests = 0;
  page.on("request", (r) => {
    if (r.url().includes("/fragments/comments/")) requests++;
  });
  await more.evaluate((b) => {
    b.click();
    b.click();
  });
  assert.equal(requests, 0);
  const root = roots.at(-1);
  let replies = page.locator(
    `[data-comment-id="${root.id}"] [data-load-replies]`,
  );
  await replies.click();
  await replies.waitFor({ state: "hidden" });
  assert.equal(await replies.isVisible(), false);
  assert.equal(await page.locator("[data-comments-empty]:visible").count(), 0);
  for (let i = 0; i < 20; i++) await comment("分页回复 " + i, root.id);
  await page.reload();
  replies = page.locator(`[data-comment-id="${root.id}"] [data-load-replies]`);
  await replies.click();
  await page.waitForFunction(
    (id) =>
      document.querySelector("#replies-" + id).querySelectorAll(".comment")
        .length === 20,
    root.id,
  );
  assert.equal(await replies.isVisible(), false);
  await comment("第 21 条回复", root.id);
  await page.reload();
  replies = page.locator(`[data-comment-id="${root.id}"] [data-load-replies]`);
  await replies.click();
  await page.waitForFunction(
    (id) =>
      document.querySelector("#replies-" + id).querySelectorAll(".comment")
        .length === 20,
    root.id,
  );
  assert.equal(await replies.isVisible(), true);
  await replies.click();
  await page.waitForFunction(
    (id) =>
      document.querySelector("#replies-" + id).querySelectorAll(".comment")
        .length === 21,
    root.id,
  );
  assert.equal(await replies.isVisible(), false);
  for (const width of [1440, 768, 375]) {
    await page.setViewportSize({ width, height: 1000 });
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
    );
    if (process.env.REDNOTE_SCREENSHOTS)
      await page.screenshot({
        path: path.join(
          process.env.REDNOTE_SCREENSHOTS,
          `comments-paged-${width}.png`,
        ),
        fullPage: true,
      });
  }
  assert.deepEqual(errors, []);
  const removed = await page.request.delete(base + "/actions/notes/" + noteId, {
    headers,
  });
  assert.equal(removed.status(), 200);
  cleanup = null;
  console.log(
    "PASS: aligned navigation, 12px composer gaps, single empty state, refresh/delete, 0/20/21 root comments and replies, end-of-list guards, 3 viewports.",
  );
  await browser.close();
})().catch(async (e) => {
  console.error(e);
  if (cleanup) await cleanup().catch(() => {});
  await browser?.close();
  process.exit(1);
});
