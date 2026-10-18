/* 发布 / 编辑。表单主体见 publish.html / edit.html，图片项使用 tpl-upload。 */
function editor(id) {
  if (!requireLogin()) return;
  const n = id ? note(id) : null;
  prepare(id ? "edit" : "publish");
  if (id && (!n || n.author !== current))
    return $(".content").replaceChildren(
      empty("无法编辑这篇笔记", "你只能编辑自己发布的笔记。"),
    );
  if (!draft || draft.id !== (n?.id || 0))
    draft = {
      id: n?.id || 0,
      title: n?.title || "",
      body: n?.body || "",
      images: (n?.images || []).map((src) => ({ src, status: "ready" })),
    };
  const back = bind("back", `← ${n ? "返回笔记" : "返回发现"}`);
  back.href = `#${n ? "/note/" + n.id : "/"}`;
  bind("heading", n ? "编辑笔记" : "记录此刻，分享热爱");
  bind("image-count", `${draft.images.length} / 9`);
  const grid = app.querySelector(".upload-grid");
  draft.images.forEach((im, i) => {
    const tile = template("upload");
    setImage(tile.querySelector("img"), im.src, `待发布图片 ${i + 1}`);
    show(tile.querySelector(".badge"), i === 0);
    const retry = tile.querySelector("[data-retry-image]");
    retry.dataset.retryImage = i;
    show(retry, im.status === "failed");
    show(tile.querySelector("[data-uploading]"), im.status === "uploading");
    tile.querySelectorAll("[data-move]").forEach((b) => {
      b.dataset.move = i;
      show(b, im.status === "ready");
      b.disabled =
        Number(b.dataset.dir) === -1 ? i === 0 : i === draft.images.length - 1;
    });
    tile.querySelector("[data-remove-image]").dataset.removeImage = i;
    grid.append(tile);
  });
  if (draft.images.length < 9) grid.append(template("upload-add"));
  $("#title").value = draft.title;
  $("#body").value = draft.body;
  $("#title-count").textContent = `${[...draft.title].length} / 50`;
  $("#body-count").textContent = `${[...draft.body].length} / 5000`;
  bind(
    "publish-notice",
    `发布后所有人可见。图片上传成功后，仍需点击${n ? "保存修改" : "发布笔记"}才能生效。`,
  );
  $("#publish-button").textContent = n ? "保存修改" : "发布笔记";
  const f = $("#editor");
  ["title", "body"].forEach(
    (k) =>
      (f.elements.namedItem(k).oninput = () => {
        draft[k] = f.elements.namedItem(k).value;
        dirty = true;
        $(`#${k}-count`).textContent =
          `${[...draft[k]].length} / ${k === "title" ? 50 : 5000}`;
      }),
  );
  $("#images")?.addEventListener("change", async (e) => {
    const files = [...e.target.files],
      epoch = viewEpoch,
      activeDraft = draft;
    for (const file of files) {
      if (draft.images.length >= 9) {
        toast("最多上传 9 张图片");
        break;
      }
      if (
        !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
        file.size > 10 * 1024 * 1024
      ) {
        toast("仅支持 JPEG、PNG、WebP，单张最大 10 MB");
        continue;
      }
      const src = await new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.readAsDataURL(file);
      });
      if (epoch !== viewEpoch || draft !== activeDraft) return;
      if (draft.images.some((i) => i.src === src)) {
        toast("同一笔记不能重复添加图片");
        continue;
      }
      const valid = await new Promise((resolve) => {
        const im = new Image();
        im.onload = () => resolve(true);
        im.onerror = () => resolve(false);
        im.src = src;
      });
      if (epoch !== viewEpoch || draft !== activeDraft) return;
      if (!valid) {
        toast("图片损坏或无法解码");
        continue;
      }
      draft.images.push({
        src,
        status: mode === "upload-error" ? "failed" : "ready",
      });
      dirty = true;
    }
    editor(id);
  });
  f.onsubmit = (e) => {
    e.preventDefault();
    clearErrors(f);
    let valid = true;
    for (const [k, max] of [
      ["title", 50],
      ["body", 5000],
    ]) {
      draft[k] = f.elements.namedItem(k).value.trim();
      if (!draft[k] || [...draft[k]].length > max) {
        fieldError(
          f,
          k,
          `请输入 1–${max} 字${k === "title" ? "标题" : "正文"}`,
        );
        valid = false;
      }
    }
    if (
      !draft.images.length ||
      draft.images.some((i) => i.status !== "ready")
    ) {
      f.querySelector('[data-error="images"]').textContent =
        "请添加 1–9 张图片，并处理上传失败的图片";
      valid = false;
    }
    if (!valid) return;
    if (mode === "error" || mode === "unknown") {
      $("#publish-error").textContent =
        mode === "unknown"
          ? "提交结果未知，请先刷新确认是否发布成功，避免重复发布。"
          : "保存失败，内容与图片已保留，请重试。";
      return;
    }
    $("#publish-button").disabled = true;
    later(() => {
      if (!me() || me().status === "禁用") {
        toast("登录已过期，输入已保留");
        $("#publish-button").disabled = false;
        return;
      }
      const data = {
        title: draft.title,
        body: draft.body,
        images: draft.images.map((i) => i.src),
      };
      if (n) Object.assign(n, data, { updated: true });
      else
        db.notes.unshift({
          ...data,
          id: Date.now(),
          author: current,
          likes: 0,
          date: "2026-10-18 18:30",
          height: 280,
        });
      const target = n?.id || db.notes[0].id;
      save();
      dirty = false;
      draft = null;
      nav("/note/" + target);
      toast(n ? "修改已保存" : "笔记已发布");
    }, 400);
  };
}

/* editor 页面按钮委托。 */
function handleEditorAction(b) {
  if (b.hasAttribute("data-remove-image")) {
    draft.images.splice(Number(b.dataset.removeImage), 1);
    dirty = true;
    render();
  }
  if (b.hasAttribute("data-move")) {
    const i = Number(b.dataset.move),
      j = i + Number(b.dataset.dir);
    [draft.images[i], draft.images[j]] = [draft.images[j], draft.images[i]];
    dirty = true;
    render();
  }
  if (b.hasAttribute("data-retry-image")) {
    draft.images[Number(b.dataset.retryImage)].status = "ready";
    render();
    toast("模拟上传成功");
  }
  if (b.hasAttribute("data-cancel-editor")) {
    if (dirty)
      confirmAction(
        "放弃本次修改？",
        "尚未保存的内容将丢失，已发布版本保持不变。",
        () => {
          dirty = false;
          draft = null;
          nav("/");
        },
        "放弃修改",
      );
    else {
      draft = null;
      nav("/");
    }
  }
  if (b.hasAttribute("data-preview")) previewDraft();
}
