/* 全局委托仅注册一次；页面表单监听器随 DOM 替换自动释放。 */
document.addEventListener("click", (e) => {
  const b = e.target.closest("button,a");
  if (!b) return;
  if (b.hasAttribute("data-close")) closeDialog();
  if (b.hasAttribute("data-demo")) demo();
  if (b.hasAttribute("data-normal")) {
    mode = "normal";
    render();
  }
  if (b.hasAttribute("data-more")) {
    limit += 20;
    const y = scrollY;
    home();
    scrollTo(0, y);
  }
  if (b.dataset.like) {
    if (!requireLogin()) return;
    const n = note(b.dataset.like);
    if (!n) return toast("笔记已删除，请刷新");
    if (mode === "error") return toast("点赞失败，状态已恢复");
    const key = `${current}:${n.id}`;
    likes[key] = !likes[key];
    n.likes += likes[key] ? 1 : -1;
    save();
    document.querySelectorAll(`[data-like="${n.id}"]`).forEach((el) => {
      el.classList.toggle("on", likes[key]);
      el.setAttribute("aria-pressed", likes[key]);
      el.setAttribute("aria-label", likes[key] ? "取消点赞" : "点赞");
      updateLike(el, n);
    });
  }
  if (b.hasAttribute("data-page")) {
    page = Number(b.dataset.page);
    render();
    scrollTo(0, 0);
  }
  handleNoteAction(b);
  handleSettingsAction(b);
  handleEditorAction(b);
  handleAdminAction(b);
  if (b.hasAttribute("data-reset"))
    confirmAction(
      "重置演示数据？",
      "本地新增的笔记、评论和资料修改将被清除。",
      () => {
        db = clone(RednoteSeed);
        likes = {};
        passwords.clear();
        login(0);
        mode = "normal";
        dirty = false;
        draft = null;
        save();
        nav("/");
        render();
        toast("演示数据已重置");
      },
      "重置",
    );
});
