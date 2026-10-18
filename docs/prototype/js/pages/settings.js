/* 个人设置：两种表单均在 settings.html 中，不在脚本里拼接字段。 */
function settings() {
  if (!requireLogin()) return;
  prepare("settings");
  const u = me(),
    password = settingsTab === "password";
  const back = bind("back", "← 返回我的主页");
  back.href = `#/profile/${current}`;
  app
    .querySelectorAll("[data-settings]")
    .forEach((b) =>
      b.classList.toggle("active", b.dataset.settings === settingsTab),
    );
  const source = document.getElementById(
    `tpl-settings-${password ? "password" : "profile"}`,
  );
  app
    .querySelector("[data-settings-fields]")
    .replaceWith(source.content.cloneNode(true));
  const form = $("#settings-form");
  form.querySelector(".primary").textContent = password
    ? "确认修改"
    : "保存资料";
  if (!password) {
    $("#avatar-preview").replaceChildren(avatar(u, "lg"));
    form.elements.username.value = u.username;
    form.elements.name.value = u.name;
    form.elements.bio.value = u.bio;
  }
  let avatarDraft = u.avatar;
  const f = $("#settings-form");
  if (!password) {
    f.elements.namedItem("username").readOnly = true;
    $("#avatar-file").onchange = async (e) => {
      const epoch = viewEpoch;
      const file = e.target.files[0];
      if (!file) return;
      if (
        !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
        file.size > 10 * 1024 * 1024
      )
        return toast("头像类型或大小不符合要求");
      const src = await new Promise((resolve) => {
        const r = new FileReader();
        r.onload = () => resolve(r.result);
        r.readAsDataURL(file);
      });
      const valid = await new Promise((resolve) => {
        let i = new Image();
        i.onload = () => resolve(true);
        i.onerror = () => resolve(false);
        i.src = src;
      });
      if (epoch !== viewEpoch) return;
      if (!valid) return toast("头像图片无法解码");
      avatarDraft = src;
      $("#avatar-preview").replaceChildren(avatar({ ...u, avatar: src }, "lg"));
    };
  }
  f.onsubmit = (e) => {
    e.preventDefault();
    clearErrors(f);
    if (password) {
      let valid = true;
      if (
        f.elements.namedItem("old").value !==
        (passwords.get(current) || "Demo1234")
      ) {
        fieldError(f, "old", "当前密码不正确");
        valid = false;
      }
      if (
        !passwordValid(f.elements.namedItem("password").value) ||
        f.elements.namedItem("password").value ===
          f.elements.namedItem("old").value
      ) {
        fieldError(f, "password", "新密码需符合规则，且不能与旧密码相同");
        valid = false;
      }
      if (
        f.elements.namedItem("confirm").value !==
        f.elements.namedItem("password").value
      ) {
        fieldError(f, "confirm", "两次新密码不一致");
        valid = false;
      }
      if (!valid) return;
      if (mode === "error")
        return ($("#settings-error").textContent = "保存失败，请重试");
      passwords.set(current, f.elements.namedItem("password").value);
      login(0);
      returnTo = "/";
      nav("/login");
      toast("密码已修改，请重新登录");
    } else {
      const name = f.elements.namedItem("name").value.trim(),
        bio = f.elements.namedItem("bio").value.trim();
      let valid = true;
      if (!name || [...name].length > 20) {
        fieldError(f, "name", "昵称请输入 1–20 字");
        valid = false;
      }
      if ([...bio].length > 200) {
        fieldError(f, "bio", "简介最多 200 字");
        valid = false;
      }
      if (!valid) return;
      if (mode === "error")
        return ($("#settings-error").textContent = "保存失败，输入已保留");
      Object.assign(u, { name, bio, avatar: avatarDraft });
      save();
      settings();
      toast("个人资料已更新");
    }
  };
}

/* settings 页面按钮委托。 */
function handleSettingsAction(b) {
  if (b.dataset.settings) {
    settingsTab = b.dataset.settings;
    settings();
  }
  if (b.hasAttribute("data-logout"))
    confirmAction(
      "退出登录？",
      "退出后仍可浏览公开笔记。",
      () => {
        login(0);
        nav("/");
        toast("已退出登录");
      },
      "退出登录",
    );
}
