import { request, error, busy, url } from "../http.js";
import { toast, dialog, safeFragment } from "../ui.js";
import { length } from "../validation.js";
export function initNote() {
  const detail = document.querySelector("[data-detail]");
  let target = null,
    slide = 0;
  const commentForm = document.querySelector("#comment-form");
  function resetReply() {
    target = null;
    if (!commentForm) return;
    commentForm.querySelector("[data-reply-label]").textContent =
      "留下你的想法";
    commentForm.querySelector("[data-cancel-reply]").hidden = true;
  }
  function updateCommentState(batch) {
    const container = detail.querySelector("[data-comments]");
    container.querySelector("[data-comments-empty]").hidden =
      !!batch.querySelector("[data-comment-id]");
    container.querySelector("[data-more-comments]").hidden =
      batch.dataset.hasMore !== "true";
    detail.querySelector("[data-comment-count]").textContent =
      "共 " + batch.dataset.total + " 条评论";
  }
  async function refreshComments() {
    const fragment = safeFragment(
      await request("/fragments/comments/" + detail.dataset.detail),
    );
    const batch = fragment.querySelector(".comment-batch");
    const container = detail.querySelector("[data-comments]");
    container.querySelector("[data-comment-list]").replaceChildren(fragment);
    container.querySelector("[data-more-comments]").dataset.page = "1";
    updateCommentState(batch);
  }
  if (commentForm) {
    commentForm.querySelector("[data-cancel-reply]").onclick = resetReply;
    commentForm.onsubmit = async (e) => {
      e.preventDefault();
      const text = commentForm.elements.text.value.trim();
      if (!text || length(text) > 500) {
        error(commentForm, new Error("评论请输入 1–500 个字符"));
        return;
      }
      await busy(commentForm.querySelector("button.btn"), async () => {
        try {
          await request(
            "/actions/notes/" + detail.dataset.detail + "/comments",
            {
              method: "POST",
              body: { text, target },
            },
          );
          commentForm.elements.text.value = "";
          commentForm.elements.text.dispatchEvent(
            new Event("input", { bubbles: true }),
          );
          resetReply();
          await refreshComments();
          const count = detail.querySelector("[data-comment-count]");
          count.textContent =
            "共 " +
            detail.querySelector(".comment-batch").dataset.total +
            " 条评论";
          error(commentForm, new Error(""));
          toast("评论成功");
        } catch (e) {
          error(commentForm, e);
        }
      });
    };
  }

  return async (b) => {
    if (!detail) return;
    if (b.hasAttribute("data-slide")) {
      const sources = [
        ...detail.querySelectorAll("[data-gallery-sources] [data-src]"),
      ];
      slide =
        (slide + Number(b.dataset.slide) + sources.length) % sources.length;
      const img = detail.querySelector("[data-gallery-image]");
      img.hidden = false;
      img.nextElementSibling?.classList.contains("image-failed") &&
        img.nextElementSibling.remove();
      img.src = sources[slide].dataset.src;
      detail.querySelector("[data-counter]").textContent =
        `${slide + 1} / ${sources.length}`;
    }
    if (b.hasAttribute("data-delete-note"))
      dialog("删除这篇笔记？", "相关点赞、评论和回复也将一并删除。", {
        confirm: async () => {
          await request("/actions/notes/" + b.dataset.deleteNote, {
            method: "DELETE",
          });
          location.assign(url("/"));
        },
      });
    if (b.hasAttribute("data-reply")) {
      target = Number(b.dataset.reply);
      commentForm.querySelector("[data-reply-label]").textContent =
        "回复 " + b.dataset.name;
      commentForm.querySelector("[data-cancel-reply]").hidden = false;
      commentForm.elements.text.focus();
    }
    if (b.hasAttribute("data-delete-comment"))
      dialog(
        "删除评论？",
        b.dataset.rootDelete === "true"
          ? "这条评论下的所有回复也将删除。"
          : "仅删除这条回复。",
        {
          confirm: async () => {
            await request("/actions/comments/" + b.dataset.deleteComment, {
              method: "DELETE",
            });
            await refreshComments();
            detail.querySelector("[data-comment-count]").textContent =
              "共 " +
              detail.querySelector(".comment-batch").dataset.total +
              " 条评论";
          },
        },
      );
    if (b.hasAttribute("data-more-comments") && !b.hidden)
      await busy(b, async () => {
        const fragment = safeFragment(
          await request(
            "/fragments/comments/" +
              detail.dataset.detail +
              "?page=" +
              b.dataset.page,
          ),
        );
        const batch = fragment.querySelector(".comment-batch");
        const hasMore = batch.dataset.hasMore === "true";
        if (batch.querySelector("[data-comment-id]"))
          detail.querySelector("[data-comment-list]").append(fragment);
        b.dataset.page = Number(b.dataset.page) + 1;
        b.hidden = !hasMore;
      });
    if (b.hasAttribute("data-load-replies") && !b.hidden)
      await busy(b, async () => {
        const fragment = safeFragment(
          await request(
            "/fragments/comments/" +
              detail.dataset.detail +
              "?root=" +
              b.dataset.loadReplies +
              "&page=" +
              b.dataset.replyPage,
          ),
        );
        const batch = fragment.querySelector(".comment-batch");
        const hasMore = batch.dataset.hasMore === "true";
        if (batch.querySelector("[data-comment-id]"))
          document
            .querySelector("#replies-" + b.dataset.loadReplies)
            .append(fragment);
        b.dataset.replyPage = Number(b.dataset.replyPage) + 1;
        b.textContent = "加载更多回复";
        b.hidden = !hasMore;
      });
  };
}
