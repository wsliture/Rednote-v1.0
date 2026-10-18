import { request, busy } from "../http.js";
import { dialog, safeFragment } from "../ui.js";
export async function handleAdmin(b) {
  if (b.hasAttribute("data-user-detail"))
    await busy(b, async () =>
      dialog(
        "用户资料",
        safeFragment(
          await request("/fragments/admin/users/" + b.dataset.userDetail),
        ),
        { drawer: true },
      ),
    );
  if (b.hasAttribute("data-user-status"))
    dialog(
      (b.dataset.status === "禁用" ? "禁用" : "启用") +
        " " +
        b.dataset.name +
        "？",
      b.dataset.status === "禁用"
        ? "该账号的所有登录状态将失效，历史公开内容保留。"
        : "启用后用户需要重新登录，旧会话不会恢复。",
      {
        confirm: async () => {
          await request(
            "/actions/admin/users/" + b.dataset.userStatus + "/status",
            { method: "PUT", body: { status: b.dataset.status } },
          );
          location.reload();
        },
      },
    );
  if (b.hasAttribute("data-refresh"))
    await busy(b, async () => {
      const existing = document.querySelector("[data-dashboard]");
      try {
        const replacement = safeFragment(
          await request("/fragments/admin/dashboard"),
        );
        existing.replaceWith(replacement);
      } catch (error) {
        existing.querySelector("[data-dashboard-error]").textContent =
          error.message;
        throw error;
      }
    });
  if (b.hasAttribute("data-admin-menu"))
    dialog(
      "管理导航",
      document.querySelector("#admin-menu").content.cloneNode(true),
    );
}
