/* 公共 DOM 绑定。所有布局取自 HTML <template>，用户文本只通过 textContent/value 展示。 */
function template(id) {
  const node = document.getElementById(`tpl-${id}`);
  if (!node) throw new Error(`缺少 HTML 模板：${id}`);
  return node.content.firstElementChild.cloneNode(true);
}
function templateChildren(id) {
  return template(id).childNodes;
}
function text(root, selector, value) {
  const el = root.querySelector(selector);
  if (el) el.textContent = value ?? "";
  return el;
}
function show(el, visible) {
  if (el) el.hidden = !visible;
}
function bind(key, value, root = app) {
  return text(root, `[data-bind="${key}"]`, value);
}
function setImage(el, src, alt) {
  el.onerror = () => imageError(el);
  el.alt = alt || "";
  el.src = src;
}
function avatar(u, size = "") {
  const el = template("avatar");
  if (size) el.classList.add(size);
  el.style.background = u?.color || "#eee";
  if (u?.avatar) {
    const im = document.createElement("img");
    im.src = u.avatar;
    im.alt = u.name;
    el.append(im);
  } else el.textContent = u?.name?.slice(0, 1) || "客";
  return el;
}
function placeAvatar(slot, u, size = "") {
  if (slot) slot.replaceWith(avatar(u, size));
}
function countComments(id) {
  return db.comments
    .filter((c) => c.note === id)
    .reduce((s, c) => s + 1 + c.replies.length, 0);
}
function liked(id) {
  return !!likes[`${current}:${id}`];
}
function updateLike(el, n) {
  const on = !!current && liked(n.id);
  el.classList.toggle("on", on);
  el.dataset.like = n.id;
  el.setAttribute("aria-label", liked(n.id) ? "取消点赞" : "点赞");
  el.setAttribute("aria-pressed", on);
  text(el, "[data-heart]", on ? "♥" : "♡");
  text(el, "[data-count]", n.likes);
}
function likeButton(n) {
  const el = template("like");
  updateLike(el, n);
  return el;
}
function cards(items) {
  const list = template("waterfall");
  items.forEach((n) => {
    const el = template("card"),
      link = el.querySelector("[data-note-link]");
    link.href = `#/note/${n.id}`;
    link.setAttribute("aria-label", `查看笔记：${n.title}`);
    el.querySelector(".cover").style.setProperty("--h", `${n.height || 280}px`);
    setImage(el.querySelector("img"), n.images[0], n.title);
    text(el, "[data-title]", n.title);
    el.querySelector("[data-author-link]").href = `#/profile/${n.author}`;
    placeAvatar(el.querySelector("[data-avatar-slot]"), user(n.author));
    text(el, "[data-author-name]", user(n.author)?.name);
    el.querySelector("[data-like-slot]").replaceWith(likeButton(n));
    list.append(el);
  });
  return list;
}
function empty(title, desc, action = "返回首页", path = "/") {
  const el = template("empty");
  text(el, "h2", title);
  text(el, "p", desc);
  const a = text(el, "a", action);
  a.href = `#${path}`;
  return el;
}
function statusContent(target, normal) {
  if (mode === "loading")
    target.replaceChildren(...templateChildren("loading"));
  else if (mode === "error") target.replaceChildren(template("error"));
  else if (mode === "empty")
    target.replaceChildren(
      empty(
        "这里还没有内容",
        "生活里的第一份分享，等你来记录。",
        "发布笔记",
        "/publish",
      ),
    );
  else normal();
}
function pagination(total) {
  const el = template("pagination"),
    max = Math.max(1, Math.ceil(total / 20));
  text(el, "[data-total]", `共 ${total} 条 · 每页 20 条`);
  text(el, "[data-page-label]", `${page} / ${max}`);
  const prev = el.querySelector("[data-page-prev]"),
    next = el.querySelector("[data-page-next]");
  prev.dataset.page = page - 1;
  prev.disabled = page <= 1;
  next.dataset.page = page + 1;
  next.disabled = page >= max;
  return el;
}
function toast(t) {
  $("#toast").textContent = t;
  $("#toast").style.display = "block";
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => ($("#toast").style.display = "none"), 3300);
}
function modal(content, drawer = false) {
  const old = document.activeElement,
    bg = template("modal"),
    box = bg.querySelector(".modal");
  box.classList.toggle("drawer", drawer);
  box.append(...(content instanceof Node ? [content] : Array.from(content)));
  $("#overlay").replaceChildren(bg);
  box.querySelector("button,input,select,a,textarea")?.focus();
  box.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeDialog();
    if (e.key === "Tab") {
      const all = [
        ...box.querySelectorAll(
          "button:not(:disabled),input,select,textarea,a[href]",
        ),
      ].filter((e) => !e.hidden && e.getClientRects().length);
      if (!all.length) return;
      const first = all[0],
        last = all.at(-1);
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  });
  modal.old = old;
}
function closeDialog() {
  $("#overlay").replaceChildren();
  if (modal.old?.isConnected) modal.old.focus();
}
function confirmAction(title, desc, fn, label = "确认") {
  const el = template("confirm");
  text(el, "h2", title);
  text(el, "p", desc);
  text(el, "#confirm-ok", label);
  modal([...el.childNodes]);
  $("#confirm-ok").onclick = () => {
    closeDialog();
    fn();
  };
}
function previewDraft() {
  const el = template("preview");
  text(el, "h2", draft.title || "未填写标题");
  text(el, "p", draft.body || "未填写正文");
  const im = el.querySelector("img");
  if (draft.images[0]) im.src = draft.images[0].src;
  else im.remove();
  modal([...el.childNodes]);
}
function imageError(im) {
  im.onerror = null;
  const src = im.src,
    box = template("image-error");
  box.querySelector("button").onclick = (e) => {
    e.preventDefault();
    box.replaceWith(im);
    im.onerror = () => im.replaceWith(box);
    im.src = src;
  };
  im.replaceWith(box);
}
function bindShell() {
  const r = route(),
    u = me(),
    isAdmin = u?.role === "管理员";
  app
    .querySelectorAll("[data-my-link]")
    .forEach((a) => (a.href = u ? `#/profile/${current}` : "#/login"));
  app
    .querySelectorAll("[data-my-name]")
    .forEach((e) => (e.textContent = u?.name || "未登录"));
  app.querySelectorAll("[data-guest]").forEach((e) => show(e, !u));
  app.querySelectorAll("[data-admin-only]").forEach((e) => show(e, isAdmin));
  app.querySelectorAll("[data-non-admin]").forEach((e) => show(e, !isAdmin));
  const foot = app.querySelector("[data-foot-user]");
  show(foot, !!u);
  if (foot && u) placeAvatar(foot.querySelector("[data-avatar-slot]"), u, "md");
  const top = app.querySelector("[data-user-avatar-link]");
  show(top, !!u);
  if (top && u) {
    top.href = `#/profile/${current}`;
    placeAvatar(top.querySelector("[data-avatar-slot]"), u, "md");
  }
  if (app.querySelector(".admin-shell"))
    placeAvatar(app.querySelector(".topbar [data-avatar-slot]"), u);
  const title = app.querySelector("[data-admin-title]");
  if (title) title.textContent = r === "/admin/users" ? "用户管理" : "数据看板";
  app
    .querySelectorAll(".navitem,.mobile-nav a")
    .forEach((a) =>
      a.classList.toggle("active", a.getAttribute("href") === `#${r}`),
    );
  // 旧原型手机“我的”入口不显示选中样式，沿用该表现。
  app
    .querySelectorAll(
      ".mobile-nav [data-my-link],.mobile-nav [data-admin-only]",
    )
    .forEach((a) => a.classList.remove("active"));
  const search = $("#search-form");
  if (search) {
    search.elements.q.value = r.startsWith("/search")
      ? new URLSearchParams(r.split("?")[1]).get("q") || ""
      : "";
    search.onsubmit = (e) => {
      e.preventDefault();
      const q = search.elements.q.value.trim();
      if ([...q].length > 100) return toast("搜索关键词最多 100 字");
      limit = 20;
      mode = "normal";
      nav(q ? "/search?q=" + encodeURIComponent(q) : "/");
    };
  }
}
function demo() {
  const el = template("demo"),
    roles = el.querySelector("#demo-role");
  roles.replaceChildren(new Option("游客", "0"));
  db.users
    .slice(0, 5)
    .forEach((u) =>
      roles.add(
        new Option(`${u.name} · ${u.role} · ${u.status}`, String(u.id)),
      ),
    );
  roles.value = String(current);
  el.querySelector("#demo-state").value = mode;
  modal([...el.childNodes]);
  $("#apply-demo").onclick = () => {
    const id = Number($("#demo-role").value),
      s = $("#demo-state").value;
    mode = s;
    login(s === "expired" ? 0 : id);
    closeDialog();
    if (id && user(id)?.status === "禁用") {
      login(0);
      toast("账号已禁用：禁止登录，仍可游客浏览");
    }
    if (s === "expired") {
      toast("登录已过期；表单输入保留，请重新登录");
      if (draft) {
        returnTo = route();
        return;
      }
    }
    render();
  };
}
