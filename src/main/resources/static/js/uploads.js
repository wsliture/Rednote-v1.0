import { request, error, url } from "./http.js";
export function uploads(form, changed) {
  const grid = form.querySelector("[data-upload-grid]"),
    input = form.querySelector("[data-upload]");
  if (!grid) return null;
  const items = [...grid.children].map((el) => ({
    id: el.dataset.uploadId,
    url: el.dataset.uploadUrl,
    state: "ready",
  }));
  function render() {
    grid.replaceChildren();
    items.forEach((item, index) => {
      const box = document.createElement("div");
      box.className = "upload-item upload-tile";
      if (item.url) {
        const img = document.createElement("img");
        img.src = item.url;
        img.alt = `笔记图片 ${index + 1}`;
        box.append(img);
      }
      const tag = document.createElement("span");
      tag.className = "tag";
      tag.textContent = index === 0 ? "封面" : String(index + 1);
      box.append(tag);
      const controls = document.createElement("div");
      controls.className = "upload-tools";
      const button = (label, action, disabled = false) => {
        const b = document.createElement("button");
        b.type = "button";
        b.textContent = label;
        b.disabled = disabled;
        b.onclick = action;
        controls.append(b);
      };
      button(
        "前移",
        () => {
          [items[index - 1], items[index]] = [item, items[index - 1]];
          changed();
          render();
        },
        index === 0,
      );
      button(
        "后移",
        () => {
          [items[index + 1], items[index]] = [item, items[index + 1]];
          changed();
          render();
        },
        index === items.length - 1,
      );
      button("移除", () => {
        items.splice(index, 1);
        if (item.local) URL.revokeObjectURL(item.local);
        changed();
        render();
      });
      if (item.state === "failed") button("重试", () => send(item));
      box.append(controls);
      if (item.state !== "ready") {
        const status = document.createElement("p");
        status.className = "error";
        status.textContent =
          item.state === "uploading" ? "上传中…" : item.message;
        box.append(status);
      }
      if (item.state === "ready") {
        const hidden = document.createElement("input");
        hidden.type = "hidden";
        hidden.name = "images";
        hidden.value = item.id;
        box.append(hidden);
      }
      grid.append(box);
    });
    if (items.length < 9) {
      const add = document.createElement("label");
      add.className = "upload-add";
      add.htmlFor = input.id;
      add.tabIndex = 0;
      add.setAttribute("role", "button");
      add.setAttribute("aria-label", "添加笔记图片");
      const plus = document.createElement("span");
      plus.style.fontSize = "30px";
      plus.textContent = "＋";
      const text = document.createElement("span");
      text.textContent = "点击添加图片";
      const types = document.createElement("span");
      types.textContent = "JPEG / PNG / WebP";
      add.append(plus, text, types);
      add.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          input.click();
        }
      });
      grid.append(add);
    }
    form.querySelector("[data-upload-count]").textContent =
      `${items.length} / 9`;
    input.hidden = items.length >= 9;
  }
  async function send(item) {
    item.state = "uploading";
    render();
    try {
      if (item.file.size > 10 * 1024 * 1024)
        throw new Error("单张图片不能超过 10 MB");
      if (!["image/jpeg", "image/png", "image/webp"].includes(item.file.type))
        throw new Error("请选择 JPEG、PNG 或 WebP 图片");
      const body = new FormData();
      body.set("file", item.file);
      const result = await request("/actions/files", { method: "POST", body });
      item.id = result.id;
      item.url = url(result.url);
      item.state = "ready";
    } catch (e) {
      item.state = "failed";
      item.message = e.message;
      error(form, e);
    }
    render();
  }
  input.onchange = () => {
    for (const file of input.files) {
      if (items.length >= 9) {
        error(form, new Error("最多上传 9 张图片"));
        break;
      }
      const item = {
        file,
        local: URL.createObjectURL(file),
        state: "uploading",
      };
      item.url = item.local;
      items.push(item);
      send(item);
    }
    input.value = "";
    changed();
    render();
  };
  render();
  return {
    items,
    ready: () => items.length > 0 && items.every((i) => i.state === "ready"),
  };
}
