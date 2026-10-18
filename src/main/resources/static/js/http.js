export const base = document
  .querySelector('meta[name="context-path"]')
  .content.replace(/\/$/, "");
export const url = (path) => base + path;
export class RequestError extends Error {
  constructor(message, status, fields = {}) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}
export async function request(path, options = {}) {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
    options.body = JSON.stringify(options.body);
  }
  if (options.method && options.method !== "GET")
    headers.set(
      document.querySelector('meta[name="csrf-header"]').content,
      document.querySelector('meta[name="csrf-token"]').content,
    );
  let response;
  try {
    response = await fetch(url(path), {
      ...options,
      headers,
      credentials: "same-origin",
    });
  } catch {
    throw new RequestError(
      options.method && options.method !== "GET"
        ? "请求结果未知，请先刷新确认，避免重复提交。"
        : "网络连接失败，请重试。",
      0,
    );
  }
  if (!response.ok) {
    let failure;
    try {
      failure = await response.json();
    } catch {}
    throw new RequestError(
      failure?.message || "请求失败，请刷新后重试。",
      response.status,
      failure?.fields,
    );
  }
  return response.headers.get("Content-Type")?.includes("application/json")
    ? response.json()
    : response.text();
}
export function error(form, error) {
  const el = form?.querySelector("[data-form-error]");
  if (el) {
    el.textContent = error.message;
    if (error.status === 401) {
      const a = document.createElement("a");
      a.href = url(
        "/login?returnTo=" +
          encodeURIComponent(
            location.pathname.slice(base.length) + location.search,
          ),
      );
      a.textContent = " 前往登录（离开后本次输入不会保留）";
      el.append(a);
    }
  }
  for (const [name, message] of Object.entries(error.fields || {})) {
    const target = form?.querySelector(`[data-error="${CSS.escape(name)}"]`);
    if (target) target.textContent = message;
  }
}
export async function busy(button, work) {
  if (button.disabled) return;
  button.disabled = true;
  try {
    return await work();
  } finally {
    button.disabled = false;
  }
}
// Enhance ordinary PRG forms without replacing pages or losing inputs on failed requests.
export async function submitForm(form) {
  const headers = new Headers({
    "X-Rednote-Form": "1",
    Accept: "application/json",
  });
  headers.set(
    document.querySelector('meta[name="csrf-header"]').content,
    document.querySelector('meta[name="csrf-token"]').content,
  );
  const body = new URLSearchParams();
  for (const [name, value] of new FormData(form))
    if (typeof value === "string") body.append(name, value);
  let response;
  try {
    response = await fetch(form.action, {
      method: "POST",
      body,
      headers,
      credentials: "same-origin",
    });
  } catch {
    throw new RequestError("请求结果未知，请先刷新确认，避免重复提交。", 0);
  }
  if (!response.ok) {
    let failure;
    try {
      failure = await response.json();
    } catch {}
    throw new RequestError(
      failure?.message || "提交失败，请刷新确认后重试。",
      response.status,
      failure?.fields,
    );
  }
  if (!response.redirected)
    throw new RequestError("提交结果未知，请先刷新确认。", 0);
  location.assign(response.url);
}
