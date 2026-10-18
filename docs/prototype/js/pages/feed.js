/* 首页 / 搜索 / 个人主页：只绑定数据；瀑布流和卡片结构见各页 HTML。 */
function home() {
  const r = route(),
    q = new URLSearchParams(r.split("?")[1]).get("q") || "";
  prepare(r.startsWith("/search") ? "search" : "index");
  const items = listNotes(q);
  bind("eyebrow", q ? "SEARCH RESULTS" : "EXPLORE THE EVERYDAY");
  bind("heading", q ? `「${q}」的搜索结果` : "发现生活的另一种可能");
  bind(
    "summary",
    q
      ? `共 ${items.length} 篇笔记 · 按最新发布排序`
      : "一些日常，一点灵感。和有趣的人，遇见值得记录的每一天。",
  );
  const target = app.querySelector("[data-results]");
  statusContent(target, () => {
    if (items.length)
      target.replaceChildren(
        cards(items.slice(0, limit)),
        template(items.length > limit ? "load-more" : "list-end"),
      );
    else
      target.replaceChildren(
        empty(
          q ? "没有找到相关笔记" : "还没有笔记",
          q
            ? "试试其他关键词，或回到首页发现更多内容。"
            : "分享你的生活，成为第一位记录者。",
        ),
      );
  });
}
function profile(id) {
  prepare("profile");
  const u = user(id);
  if (!u)
    return $(".content").replaceChildren(
      empty("用户不存在", "该用户资料暂不可用。"),
    );
  const items = listNotes("", u.id);
  placeAvatar(app.querySelector("[data-profile-avatar]"), u, "lg");
  bind("name", u.name);
  bind("bio", u.bio || "还没有填写简介，生活的故事正在发生。");
  bind("note-count", `${items.length} 篇笔记`);
  app
    .querySelectorAll("[data-owner-only]")
    .forEach((e) => show(e, current === u.id));
  text(app, "[data-tab-label]", `${current === u.id ? "我的" : "已发布"}笔记`);
  text(app, "[data-tab-count]", items.length);
  const target = app.querySelector("[data-results]");
  statusContent(target, () => {
    if (items.length)
      target.replaceChildren(
        cards(items.slice((page - 1) * 20, page * 20)),
        pagination(items.length),
      );
    else
      target.replaceChildren(
        empty(
          "还没有发布笔记",
          "用一篇笔记，记录生活的第一份美好。",
          current === u.id ? "去发布" : "返回首页",
          current === u.id ? "/publish" : "/",
        ),
      );
  });
}
