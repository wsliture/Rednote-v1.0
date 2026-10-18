/* 登录、注册：布局与字段见 login.html / register.html。 */
function auth(register = false) {
  prepare(register ? "register" : "login");
  $("#auth-form").onsubmit = (e) => {
    e.preventDefault();
    const f = e.target,
      un = f.elements.namedItem("username").value.trim(),
      pw = f.elements.namedItem("password").value;
    clearErrors(f);
    let valid = true;
    if (!/^[a-zA-Z0-9_]{4,20}$/.test(un)) {
      fieldError(f, "username", "用户名需为 4–20 位字母、数字或下划线");
      valid = false;
    }
    if (!passwordValid(pw)) {
      fieldError(f, "password", "密码需为 8–32 位，至少包含一个字母和一个数字");
      valid = false;
    }
    if (register && f.elements.namedItem("confirm").value !== pw) {
      fieldError(f, "confirm", "两次输入的密码不一致");
      valid = false;
    }
    if (!valid) return;
    if (mode === "error")
      return ($("#auth-error").textContent = "网络异常，请重试");
    let u = db.users.find((u) => u.username.toLowerCase() === un.toLowerCase());
    if (register) {
      if (u) return fieldError(f, "username", "该用户名已被使用");
      u = {
        id: Date.now(),
        username: un,
        name: un,
        bio: "",
        role: "普通用户",
        status: "正常",
        date: "2026-10-18",
        color: "#e5ded6",
      };
      db.users.push(u);
      passwords.set(u.id, pw);
      save();
      nav("/login");
      toast("注册成功，请登录；新密码仅在本次页面会话内有效");
    } else {
      if (!u || pw !== (passwords.get(u.id) || "Demo1234"))
        return ($("#auth-error").textContent = "账号或密码错误");
      if (u.status === "禁用")
        return ($("#auth-error").textContent = "账号已禁用");
      login(u.id);
      nav(returnTo || "/");
      toast("登录成功，请继续之前的操作");
    }
  };
}
function passwordValid(p) {
  return (
    [...p].length >= 8 &&
    [...p].length <= 32 &&
    /[a-zA-Z]/.test(p) &&
    /[0-9]/.test(p)
  );
}

function clearErrors(f) {
  f.querySelectorAll(".error").forEach((x) => (x.textContent = ""));
}

function fieldError(f, name, text) {
  f.querySelector(`[data-error="${name}"]`).textContent = text;
  f.elements[name]?.setAttribute("aria-invalid", "true");
}
