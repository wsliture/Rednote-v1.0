/* 后台页面的数据绑定；布局、SVG 趋势图及表格模板均位于 HTML。 */
function adminGuard() {
  returnTo = route();
  if (me()?.role !== "管理员" || me()?.status !== "正常") {
    $(".content").replaceChildren(
      empty(
        "仅管理员可访问",
        "后台包含管理数据，请使用管理员身份进入。",
        "前往登录",
        "/login",
      ),
    );
    return false;
  }
  return true;
}
function admin() {
  prepare("admin");
  if (!adminGuard()) return;
  const values = [
    db.users.filter((u) => u.role !== "管理员").length,
    db.notes.length,
    db.notes.reduce((s, n) => s + n.likes, 0),
    db.comments.reduce((s, c) => s + 1 + c.replies.length, 0),
  ];
  values.forEach((v, i) =>
    text(app, `[data-metric="${i}"]`, v.toLocaleString()),
  );
  $("#stat-time").textContent =
    `统计时间：${new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", dateStyle: "short", timeStyle: "medium" }).format(new Date())}（北京时间）`;
  statusContent(app.querySelector("[data-results]"), () => {});
}
function adminUsers() {
  prepare("admin-users");
  if (!adminGuard()) return;
  const items = listUsers(filter),
    f = $("#filter-form");
  Object.entries(filter).forEach(
    ([k, v]) => (f.elements.namedItem(k).value = v),
  );
  const target = app.querySelector("[data-results]");
  statusContent(target, () => {
    if (!items.length) {
      target.replaceChildren(
        empty(
          "没有匹配的用户",
          "请尝试调整用户名、昵称或账号状态。",
          "返回用户列表",
          "/admin/users",
        ),
      );
      return;
    }
    const tbody = target.querySelector("tbody");
    items.slice((page - 1) * 20, page * 20).forEach((u) => {
      const row = template("user-row");
      text(row, "[data-user-id]", u.id);
      text(row, "[data-username]", u.username);
      text(row, "[data-name]", u.name);
      text(row, "[data-role]", u.role);
      text(row, "[data-date]", u.date);
      placeAvatar(row.querySelector("[data-avatar-slot]"), u);
      const status = text(row, ".status", u.status);
      status.classList.toggle("off", u.status === "禁用");
      row.querySelector("[data-user-detail]").dataset.userDetail = u.id;
      const b = row.querySelector("[data-status-user]");
      b.dataset.statusUser = u.id;
      b.textContent = u.status === "正常" ? "禁用" : "启用";
      b.classList.toggle("danger", u.status === "正常");
      show(b, u.role !== "管理员");
      tbody.append(row);
    });
    target.append(pagination(items.length));
  });
  f.onsubmit = (e) => {
    e.preventDefault();
    filter = Object.fromEntries(new FormData(f));
    filter.username = filter.username.trim();
    filter.name = filter.name.trim();
    page = 1;
    adminUsers();
  };
}
function userDetail(id) {
  if (me()?.role !== "管理员") return;
  const u = user(id),
    el = template("user-detail");
  placeAvatar(el.querySelector("[data-avatar-slot]"), u, "lg");
  for (const [key, value] of Object.entries({
    name: u.name,
    "user-id": u.id,
    username: u.username,
    role: u.role,
    date: u.date,
    bio: u.bio || "未填写",
    "note-count": `${db.notes.filter((n) => n.author === u.id).length} 篇`,
  }))
    text(el, `[data-${key}]`, value);
  const status = text(el, ".status", u.status);
  status.classList.toggle("off", u.status === "禁用");
  const b = el.querySelector("[data-status-user]");
  b.dataset.statusUser = u.id;
  b.textContent = `${u.status === "正常" ? "禁用" : "启用"}此账号`;
  b.classList.toggle("danger", u.status === "正常");
  show(b, u.role !== "管理员");
  show(el.querySelector("[data-admin-hint]"), u.role === "管理员");
  modal([...el.childNodes], true);
}

/* admin 页面按钮委托。 */
function handleAdminAction(b) {
  if (b.hasAttribute("data-refresh")) {
    mode = "normal";
    admin();
    toast("数据已刷新");
  }
  if (b.hasAttribute("data-reset-filter")) {
    filter = { username: "", name: "", status: "" };
    page = 1;
    adminUsers();
  }
  if (b.dataset.userDetail) userDetail(b.dataset.userDetail);
  if (b.dataset.statusUser) {
    if (me()?.role !== "管理员") return;
    const u = user(b.dataset.statusUser);
    if (u.role === "管理员") return;
    const disable = u.status === "正常";
    confirmAction(
      `${disable ? "禁用" : "启用"}用户「${u.name}」？`,
      disable
        ? "该账号将立即退出所有登录会话，无法登录或写入；历史公开内容保留。"
        : "启用后用户需重新登录，旧会话不会恢复。",
      () => {
        u.status = disable ? "禁用" : "正常";
        save();
        render();
        toast(`账号已${u.status === "正常" ? "启用" : "禁用"}`);
      },
      disable ? "确认禁用" : "确认启用",
    );
  }
  if (b.hasAttribute("data-admin-menu")) modal(templateChildren("admin-menu"));
}
