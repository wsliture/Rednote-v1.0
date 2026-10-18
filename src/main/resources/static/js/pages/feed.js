import { request } from "../http.js";
import { safeFragment } from "../ui.js";
export function initFeed() {
  const feed = document.querySelector("[data-feed]");
  let loading = false,
    feedStopped = false;
  async function loadFeed() {
    if (!feed || loading || feedStopped) return;
    const batches = feed.querySelectorAll(".feed-batch"),
      last = batches[batches.length - 1];
    if (last.dataset.end === "true") return;
    loading = true;
    const button = feed.querySelector("[data-load-feed]"),
      status = feed.querySelector("[data-feed-status]");
    button.disabled = true;
    status.textContent = "正在加载…";
    try {
      const fragment = safeFragment(
        await request(
          "/fragments/feed?q=" +
            encodeURIComponent(feed.dataset.query || "") +
            "&cursor=" +
            encodeURIComponent(last.dataset.next),
        ),
      );
      const ids = new Set(
        [...feed.querySelectorAll("[data-note-id]")].map(
          (el) => el.dataset.noteId,
        ),
      );
      fragment.querySelectorAll("[data-note-id]").forEach((card) => {
        if (ids.has(card.dataset.noteId)) card.remove();
      });
      const end = fragment.querySelector(".feed-batch").dataset.end === "true";
      feed.querySelector(".waterfall").append(fragment);
      button.hidden = end;
      status.textContent = end ? "已经看完全部笔记" : "";
    } catch (e) {
      status.textContent = e.message;
      feedStopped = true;
      button.textContent = "加载失败，点击重试";
    } finally {
      button.disabled = false;
      loading = false;
    }
  }
  if (feed) {
    feed.querySelector("[data-load-feed]")?.addEventListener("click", () => {
      feedStopped = false;
      loadFeed();
    });
    window.addEventListener(
      "scroll",
      () => {
        if (
          innerHeight + scrollY >=
          document.documentElement.scrollHeight - 300
        )
          loadFeed();
      },
      { passive: true },
    );
  }
}
