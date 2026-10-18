import { request, error, url, submitForm } from "../http.js";
import { toast, dialog } from "../ui.js";
import { validate } from "../validation.js";
import { uploads } from "../uploads.js";
export function initForms() {
  const editor = document.querySelector("[data-editor]");
  let dirty = false,
    submitting = false;
  const upload = editor ? uploads(editor, () => (dirty = true)) : null;
  if (editor) {
    editor.addEventListener("input", () => (dirty = true));
    window.addEventListener("beforeunload", (e) => {
      if (dirty && !submitting) {
        e.preventDefault();
        e.returnValue = "";
      }
    });
    document.addEventListener("click", (e) => {
      const a = e.target.closest("a[href]");
      if (!a || !dirty || submitting || e.ctrlKey || e.metaKey) return;
      e.preventDefault();
      dialog("放弃未保存修改？", "离开后，本次未保存的修改将丢失。", {
        confirm: () => {
          dirty = false;
          location.assign(a.href);
        },
      });
    });
    editor.querySelector("[data-preview]").onclick = () => {
      const content = document.createElement("div");
      const title = document.createElement("h3");
      title.textContent = editor.elements.title.value || "未填写标题";
      content.append(title);
      if (upload.items[0]?.url) {
        const img = document.createElement("img");
        img.src = upload.items[0].url;
        img.style.cssText =
          "max-width:100%;max-height:300px;object-fit:contain";
        content.append(img);
      }
      const body = document.createElement("p");
      body.style.whiteSpace = "pre-wrap";
      body.textContent = editor.elements.body.value || "未填写正文";
      content.append(body);
      dialog("笔记预览", content);
    };
  }
  for (const form of document.querySelectorAll('form[method="post"]')) {
    const send = async () => {
      const buttons = [
        ...form.querySelectorAll('button[type="submit"],button:not([type])'),
      ];
      buttons.forEach((b) => (b.disabled = true));
      submitting = true;
      try {
        await submitForm(form);
      } catch (e) {
        submitting = false;
        buttons.forEach((b) => (b.disabled = false));
        error(form, e);
        if (!form.querySelector("[data-form-error]")) toast(e.message);
        throw e;
      }
    };
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      if (submitting) return;
      if (form.matches("[data-logout]")) {
        dialog("退出登录？", "退出后需要重新登录才能发布和互动。", {
          confirm: send,
        });
        return;
      }
      if (form.dataset.uploadPending === "true") {
        error(form, new Error("请等待头像上传完成"));
        return;
      }
      if (!validate(form)) return;
      if (form === editor && !upload.ready()) {
        error(form, new Error("请上传 1–9 张图片，并处理上传中或失败的图片。"));
        return;
      }
      try {
        await send();
      } catch {
        /* The form retains input, uploaded images and its inline error. */
      }
    });
  }
  const avatar = document.querySelector("#avatar-file");
  let avatarEpoch = 0;
  if (avatar)
    avatar.onchange = async () => {
      if (!avatar.files.length) return;
      const epoch = ++avatarEpoch;
      const form = avatar.form,
        button = form.querySelector("button");
      button.disabled = true;
      form.dataset.uploadPending = "true";
      try {
        const body = new FormData();
        body.set("file", avatar.files[0]);
        const file = await request("/actions/files", { method: "POST", body });
        if (epoch !== avatarEpoch) return;
        form.elements.avatar.value = file.id;
        form.querySelector("[data-avatar-preview]").src = url(file.url);
      } catch (e) {
        if (epoch === avatarEpoch) error(form, e);
      } finally {
        if (epoch === avatarEpoch) {
          form.dataset.uploadPending = "false";
          button.disabled = false;
          avatar.value = "";
        }
      }
    };
  document.querySelectorAll("[data-tab]").forEach(
    (button) =>
      (button.onclick = () => {
        document
          .querySelectorAll("[data-tab]")
          .forEach((b) => b.classList.toggle("active", b === button));
        document
          .querySelectorAll("[data-tab-panel]")
          .forEach(
            (p) => (p.hidden = p.dataset.tabPanel !== button.dataset.tab),
          );
      }),
  );
}
