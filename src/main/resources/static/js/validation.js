export const length = (value) => [...value].length;
export function validate(form) {
  form.querySelectorAll("[data-error]").forEach((x) => (x.textContent = ""));
  const values = new FormData(form),
    failures = {};
  const limit = (name, min, max) => {
    if (!values.has(name)) return;
    const value = String(values.get(name)).trim();
    if (length(value) < min || length(value) > max)
      failures[name] = `请输入 ${min}–${max} 个字符`;
  };
  if (form.matches("[data-editor]")) {
    limit("title", 1, 50);
    limit("body", 1, 5000);
  }
  if (form.matches("[data-profile]")) {
    limit("name", 1, 20);
    limit("bio", 0, 200);
  }
  if (form.matches('[data-auth="register"]')) {
    if (!/^[a-zA-Z0-9_]{4,20}$/.test(String(values.get("username")).trim()))
      failures.username = "用户名须为 4–20 位字母、数字或下划线";
  }
  if (form.matches('[data-password],[data-auth="register"]')) {
    const p = String(values.get("password"));
    if (
      length(p) < 8 ||
      length(p) > 32 ||
      !/[A-Za-z]/.test(p) ||
      !/[0-9]/.test(p)
    )
      failures.password = "密码须为 8–32 位，包含英文字母和数字";
    if (p !== values.get("confirm")) failures.confirm = "两次密码不一致";
    if (form.matches("[data-password]") && !values.get("old"))
      failures.old = "请输入旧密码";
  }
  for (const [name, message] of Object.entries(failures)) {
    const el = form.querySelector(`[data-error="${name}"]`);
    if (el) el.textContent = message;
  }
  if (Object.keys(failures).length) {
    const error = form.querySelector("[data-form-error]");
    if (error) error.textContent = Object.values(failures)[0];
    return false;
  }
  return true;
}
export function counters() {
  document.querySelectorAll("[data-counter-for]").forEach((counter) => {
    const field = counter
      .closest("form")
      .elements.namedItem(counter.dataset.counterFor);
    if (!field) return;
    const update = () =>
      (counter.textContent = `${length(field.value)} / ${counter.dataset.max}`);
    field.addEventListener("input", update);
    update();
  });
}
