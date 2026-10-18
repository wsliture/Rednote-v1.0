/* 原型专用导航：读取独立 HTML 的 #app / #templates，永不执行其中的 script。
 * Thymeleaf 迁移时移除此层，交由服务端路由渲染完整页面。
 */
const pageDocuments = new Map();
let navigationSequence = 0,
  navigationRequest = null;
function rememberDocument(name, doc) {
  const root = doc.querySelector("#app"),
    templates = doc.querySelector("#templates");
  if (!root || !templates) throw new Error("页面缺少布局或模板");
  pageDocuments.set(name, {
    root: root.cloneNode(true),
    templates: templates.cloneNode(true),
    title: doc.title,
  });
}
function prepare(name) {
  const source = pageDocuments.get(name);
  if (!source) throw new Error(`尚未加载页面：${name}`);
  cancelPageWork();
  app.replaceChildren(
    ...Array.from(source.root.childNodes, (n) => n.cloneNode(true)),
  );
  $("#templates").replaceWith(source.templates.cloneNode(true));
  document.title = source.title;
  document.body.dataset.page = name;
  bindShell();
}
function pageFor(r) {
  if (r === "/admin/users") return "admin-users";
  if (r === "/admin") return "admin";
  if (
    r === "/login" ||
    r === "/register" ||
    r === "/settings" ||
    r === "/publish"
  )
    return r.slice(1);
  if (r.startsWith("/note/")) return "note";
  if (r.startsWith("/profile/")) return "profile";
  if (r.startsWith("/edit/")) return "edit";
  if (r.startsWith("/search")) return "search";
  return "index";
}
function bindPage(r) {
  if (r === "/admin/users") adminUsers();
  else if (r === "/admin") admin();
  else if (r === "/login" || r === "/register") auth(r === "/register");
  else if (r.startsWith("/note/")) detail(r.split("/")[2]);
  else if (r.startsWith("/profile/")) profile(r.split("/")[2]);
  else if (r === "/settings") settings();
  else if (r === "/publish" || r.startsWith("/edit/"))
    editor(r.startsWith("/edit/") ? Number(r.split("/")[2]) : null);
  else if (r === "/" || r.startsWith("/search")) home();
  else {
    prepare("index");
    $(".content").replaceChildren(
      empty("页面不存在", "换个入口，继续发现生活。"),
    );
  }
}
function render() {
  const r = route(),
    name = pageFor(r),
    sequence = ++navigationSequence;
  navigationRequest?.abort();
  navigationRequest = null;
  if (pageDocuments.has(name)) {
    bindPage(r);
    return Promise.resolve(true);
  }
  const request = new AbortController();
  navigationRequest = request;
  return fetch(new URL(`${name}.html`, location.href), {
    signal: request.signal,
  })
    .then((response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return response.text();
    })
    .then((html) => {
      if (sequence !== navigationSequence || route() !== r) return false;
      rememberDocument(
        name,
        new DOMParser().parseFromString(html, "text/html"),
      );
      bindPage(r);
      return true;
    })
    .catch((error) => {
      if (error.name === "AbortError" || sequence !== navigationSequence)
        return false;
      history.replaceState(null, "", previousHash);
      toast("页面加载失败，当前内容已保留，请重试");
      confirmAction(
        "页面暂时无法打开",
        "当前页面和输入已保留。请检查本地 HTTP 服务后重试。",
        () => nav(r),
        "重试",
      );
      return false;
    });
}
function startNavigation() {
  rememberDocument(document.body.dataset.page, document);
  if (!location.hash) {
    let initial = document.body.dataset.defaultRoute || "/";
    const q = new URLSearchParams(location.search);
    if (initial.startsWith("/note/") && q.has("id"))
      initial = "/note/" + q.get("id");
    if (initial.startsWith("/profile/") && q.has("id"))
      initial = "/profile/" + q.get("id");
    if (initial.startsWith("/edit/") && q.has("id"))
      initial = "/edit/" + q.get("id");
    if (initial === "/search" && q.has("q"))
      initial += "?q=" + encodeURIComponent(q.get("q"));
    history.replaceState(null, "", "#" + initial);
  }
  previousHash = location.hash;
  window.addEventListener("hashchange", () => {
    const next = location.hash;
    if (dirty && !allowNav) {
      history.replaceState(null, "", previousHash);
      confirmAction(
        "离开编辑页面？",
        "尚未保存的修改将丢失。",
        () => {
          dirty = false;
          draft = null;
          allowNav = true;
          location.hash = next;
        },
        "放弃修改",
      );
      return;
    }
    allowNav = false;
    page = 1;
    slide = 0;
    reply = null;
    limit = 20;
    closeDialog();
    render().then((success) => {
      if (success && location.hash === next) {
        previousHash = next;
        scrollTo(0, 0);
      }
    });
  });
  window.addEventListener("beforeunload", (e) => {
    if (dirty) {
      e.preventDefault();
      e.returnValue = "";
    }
  });

  window.addEventListener("scroll", () => {
    if (
      feedScrollPending ||
      mode !== "normal" ||
      !(route() === "/" || route().startsWith("/search"))
    )
      return;
    if (
      innerHeight + scrollY >= document.documentElement.scrollHeight - 300 &&
      $("[data-more]")
    ) {
      feedScrollPending = true;
      later(() => {
        $("[data-more]")?.click();
        feedScrollPending = false;
      }, 250);
    }
  });
  render();
}
