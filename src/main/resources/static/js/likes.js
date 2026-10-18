import { request, busy, url, base } from "./http.js";
import { dialog } from "./ui.js";
export async function handleLike(b) {
  if (b.hasAttribute("data-like"))
    await busy(b, async () => {
      try {
        const result = await request(
          "/actions/notes/" + b.dataset.like + "/like",
          {
            method: "PUT",
            body: { liked: b.getAttribute("aria-pressed") !== "true" },
          },
        );
        document
          .querySelectorAll('[data-like="' + b.dataset.like + '"]')
          .forEach((el) => {
            el.setAttribute("aria-pressed", String(result.liked));
            el.setAttribute("aria-label", result.liked ? "取消点赞" : "点赞");
            el.classList.toggle("liked", result.liked);
            el.querySelector("[data-heart]").textContent = result.liked
              ? "♥"
              : "♡";
            el.querySelector("[data-count]").textContent = result.likes;
          });
      } catch (err) {
        if (err.status === 401)
          dialog("请先登录", "登录后可点赞。登录成功后请自行继续操作。", {
            confirm: () =>
              location.assign(
                url(
                  "/login?returnTo=" +
                    encodeURIComponent(
                      location.pathname.slice(base.length) + location.search,
                    ),
                ),
              ),
          });
        else throw err;
      }
    });
}
