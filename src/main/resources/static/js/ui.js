let timer;
export function toast(message) {
  const el = document.querySelector("#toast");
  el.textContent = message;
  el.classList.add("show");
  el.style.display = "block";
  clearTimeout(timer);
  timer = setTimeout(() => {
    el.classList.remove("show");
    el.style.display = "none";
  }, 3300);
}
export function dialog(title, content, { confirm, drawer = false } = {}) {
  const host = document.querySelector("#overlay"),
    previous = document.activeElement;
  host.replaceChildren();
  const bg = document.createElement("div");
  bg.className = "modal-bg";
  const panel = document.createElement("section");
  panel.className = "modal";
  if (drawer) {
    bg.style.padding = "0";
    panel.style.cssText =
      "margin-left:auto;height:100dvh;max-height:100dvh;width:430px;max-width:100%;border-radius:20px 0 0 20px";
  }
  panel.setAttribute("role", "dialog");
  panel.setAttribute("aria-modal", "true");
  panel.setAttribute("aria-labelledby", "dialog-title");
  const heading = document.createElement("h2");
  heading.id = "dialog-title";
  heading.textContent = title;
  panel.append(heading);
  if (typeof content === "string") {
    const p = document.createElement("p");
    p.textContent = content;
    panel.append(p);
  } else panel.append(content);
  const controls = document.createElement("div");
  controls.className = "row";
  controls.style.justifyContent = "flex-end";
  const close = document.createElement("button");
  close.className = "btn";
  close.textContent = confirm ? "取消" : "关闭";
  const cleanup = () => {
    document.removeEventListener("keydown", keys);
    document.body.classList.remove("dialog-open");
    host.replaceChildren();
    if (previous?.isConnected) previous.focus();
  };
  close.onclick = cleanup;
  controls.append(close);
  const failure = document.createElement("p");
  failure.className = "error";
  failure.setAttribute("role", "alert");
  failure.hidden = true;
  panel.append(failure);
  if (confirm) {
    const ok = document.createElement("button");
    ok.className = "btn primary";
    ok.textContent = "确认";
    ok.onclick = async () => {
      ok.disabled = true;
      try {
        await confirm();
        cleanup();
      } catch (e) {
        failure.textContent = e.message;
        failure.hidden = false;
        toast(e.message);
        ok.disabled = false;
      }
    };
    controls.append(ok);
  }
  panel.append(controls);
  bg.append(panel);
  host.append(bg);
  document.body.classList.add("dialog-open");
  function keys(e) {
    if (e.key === "Escape") {
      e.preventDefault();
      cleanup();
    }
    if (e.key === "Tab") {
      const focus = [
        ...panel.querySelectorAll(
          'button,a[href],input,textarea,select,[tabindex="0"]',
        ),
      ].filter((x) => !x.disabled && !x.hidden);
      if (e.shiftKey && document.activeElement === focus[0]) {
        e.preventDefault();
        focus.at(-1).focus();
      } else if (!e.shiftKey && document.activeElement === focus.at(-1)) {
        e.preventDefault();
        focus[0].focus();
      }
    }
  }
  document.addEventListener("keydown", keys);
  panel.querySelector("button,a,input")?.focus();
  return cleanup;
}
export function safeFragment(html) {
  const template = document.createElement("template");
  template.innerHTML = html;
  return template.content;
}
export function imageErrors() {
  document.addEventListener(
    "error",
    (e) => {
      const img = e.target;
      if (
        !(img instanceof HTMLImageElement) ||
        !img.hasAttribute("data-image") ||
        img.nextElementSibling?.classList.contains("image-failed")
      )
        return;
      const box = document.createElement("div");
      box.className = "image-failed";
      const text = document.createElement("span");
      text.textContent = "图片暂时无法加载";
      const retry = document.createElement("button");
      retry.type = "button";
      retry.textContent = "重试";
      retry.onclick = (ev) => {
        ev.preventDefault();
        ev.stopPropagation();
        box.remove();
        img.hidden = false;
        img.src = img.src;
      };
      box.append(text, retry);
      img.hidden = true;
      img.after(box);
    },
    true,
  );
}
