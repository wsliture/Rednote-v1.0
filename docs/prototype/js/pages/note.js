/* 详情、评论和回复。DOM 结构位于 note.html 的正文及重复项模板中。 */
function detail(id) {
  prepare("note");
  const n = note(id);
  if (!n)
    return $(".content").replaceChildren(
      empty("笔记不存在或已删除", "去首页看看，还有更多值得记录的生活。"),
    );
  const u = user(n.author),
    cs = db.comments.filter((c) => c.note === n.id).sort((a, b) => b.id - a.id);
  slide = Math.min(slide, n.images.length - 1);
  setImage(
    app.querySelector('[data-bind="gallery-image"]'),
    n.images[slide],
    `笔记图片 ${slide + 1}`,
  );
  bind("image-counter", `${slide + 1} / ${n.images.length}`);
  placeAvatar(app.querySelector("[data-note-avatar]"), u, "md");
  app.querySelector('[data-bind="author-link"]').href = `#/profile/${u.id}`;
  bind("author-name", u.name);
  show(app.querySelector('[data-bind="owner-actions"]'), current === n.author);
  app.querySelector('[data-bind="owner-actions"] a').href = `#/edit/${n.id}`;
  app.querySelector("[data-delete-note]").dataset.deleteNote = n.id;
  bind("title", n.title);
  bind("body", n.body);
  bind("date", `${n.date} 发布${n.updated ? " · 已编辑" : ""}`);
  bind("comment-count", `共 ${countComments(n.id)} 条评论`);
  app.querySelector("[data-note-like]").replaceWith(likeButton(n));
  const list = app.querySelector(".comments");
  if (cs.length)
    cs.slice(0, page * 20).forEach((c) => list.append(commentHtml(c)));
  else list.append(template("empty-comments"));
  if (cs.length > page * 20) {
    const more = template("more");
    more.textContent = "加载更多评论";
    more.dataset.commentsMore = "";
    list.append(more);
  }
  const form = $("#comment-form");
  form.dataset.note = n.id;
  bind("reply-label", reply ? `回复 ${reply.name}` : "留下你的想法");
  show(form.querySelector("[data-cancel-reply]"), !!reply);
  const input = form.elements.text;
  input.placeholder = me() ? "友善交流，让分享更有温度" : "登录后参与讨论";
  input.value = commentDrafts.get(n.id) || "";
  input.oninput = () => commentDrafts.set(n.id, input.value);
  form.onsubmit = submitComment;
}
function commentHtml(c) {
  const fragment = document.createDocumentFragment(),
    el = template("comment"),
    u = user(c.author);
  placeAvatar(el.querySelector("[data-avatar-slot]"), u);
  const author = text(el, "[data-author-link]", u.name);
  author.href = `#/profile/${u.id}`;
  multiline(el.querySelector("[data-comment-text]"), c.text);
  text(el, "[data-date]", c.date);
  const rb = el.querySelector("[data-reply]");
  rb.dataset.reply = c.id;
  rb.dataset.target = u.id;
  const del = el.querySelector("[data-delete-comment]");
  del.dataset.deleteComment = c.id;
  show(del, current === c.author);
  fragment.append(el);
  if (c.replies.length) {
    const group = template("reply-group"),
      items = expanded.has(c.id)
        ? c.replies.slice(0, (c.replyPage || 1) * 20)
        : c.replies.slice(0, 1);
    items.forEach((r) => {
      const row = template("reply");
      placeAvatar(row.querySelector("[data-avatar-slot]"), user(r.author));
      const author = text(row, "[data-author-link]", user(r.author)?.name);
      author.href = `#/profile/${r.author}`;
      text(row, "[data-target-name]", r.target);
      multiline(row.querySelector("[data-comment-text]"), r.text);
      text(row, "[data-date]", r.date);
      const rb = row.querySelector("[data-reply]");
      rb.dataset.reply = c.id;
      rb.dataset.target = r.author;
      rb.dataset.targetReply = r.id;
      const del = row.querySelector("[data-delete-reply]");
      del.dataset.deleteReply = r.id;
      del.dataset.parent = c.id;
      show(del, current === r.author);
      group.append(row);
    });
    if (!expanded.has(c.id) && c.replies.length > 1) {
      const more = template("more");
      more.textContent = `展开 ${c.replies.length - 1} 条回复 ⌄`;
      more.dataset.expand = c.id;
      group.append(more);
    } else if (
      expanded.has(c.id) &&
      c.replies.length > (c.replyPage || 1) * 20
    ) {
      const more = template("more");
      more.textContent = "加载更多回复";
      more.dataset.repliesMore = c.id;
      group.append(more);
    }
    fragment.append(group);
  }
  return fragment;
}
function multiline(el, value) {
  const lines = String(value).split("\n");
  el.replaceChildren();
  lines.forEach((line, i) => {
    if (i) el.append(document.createElement("br"));
    el.append(document.createTextNode(line));
  });
}
function submitComment(e) {
  e.preventDefault();
  const form = e.target,
    text = form.text.value.trim();
  if (!me()) {
    commentDrafts.set(Number(form.dataset.note), form.text.value);
    returnTo = route();
    nav("/login");
    toast("请先登录，评论内容已暂存，登录后请继续提交");
    return;
  }
  if (!requireLogin()) return;
  if ([...text].length < 1 || [...text].length > 500) {
    $("#comment-error").textContent = "评论请输入 1–500 字";
    return;
  }
  if (mode === "error" || mode === "unknown") {
    toast(
      mode === "unknown"
        ? "提交结果未知，请先刷新确认，输入已保留"
        : "网络异常，输入已保留，请重试",
    );
    return;
  }
  const n = note(form.dataset.note);
  if (!n) {
    toast("笔记已删除，请刷新");
    return;
  }
  const id = Date.now();
  if (reply) {
    const c = db.comments.find((c) => c.id === reply.parent);
    if (
      !c ||
      (reply.targetReply && !c.replies.some((r) => r.id === reply.targetReply))
    ) {
      toast("回复目标已删除，请刷新，输入已保留");
      return;
    }
    c.replies.push({
      id,
      author: current,
      target: reply.name,
      text,
      date: "刚刚",
    });
    expanded.add(c.id);
  } else
    db.comments.push({
      id,
      note: n.id,
      author: current,
      text,
      date: "刚刚",
      replies: [],
    });
  reply = null;
  commentDrafts.delete(n.id);
  save();
  detail(n.id);
  toast("评论已发布");
}

/* note 页面按钮委托。 */
function handleNoteAction(b) {
  if (b.dataset.slide) {
    slide =
      (slide +
        Number(b.dataset.slide) +
        note(route().split("/")[2]).images.length) %
      note(route().split("/")[2]).images.length;
    render();
  }
  if (b.dataset.deleteNote) {
    const id = Number(b.dataset.deleteNote);
    if (note(id)?.author !== current) return;
    confirmAction(
      "删除这篇笔记？",
      "删除后笔记将不再展示，相关点赞、评论和回复也会一并移除。此操作无法撤销。",
      () => {
        db.notes = db.notes.filter((n) => n.id !== id);
        db.comments = db.comments.filter((c) => c.note !== id);
        save();
        nav("/profile/" + current);
        toast("笔记已删除");
      },
      "确认删除",
    );
  }
  if (b.dataset.reply) {
    if (!requireLogin()) return;
    const text = $("#comment-form textarea")?.value || "";
    reply = {
      parent: Number(b.dataset.reply),
      name: user(b.dataset.target).name,
      targetReply: Number(b.dataset.targetReply) || 0,
    };
    render();
    $("#comment-form textarea").value = text;
    $("#comment-form textarea").focus();
  }
  if (b.hasAttribute("data-cancel-reply")) {
    let t = $("#comment-form textarea").value;
    reply = null;
    render();
    $("#comment-form textarea").value = t;
  }
  if (b.dataset.expand) {
    expanded.add(Number(b.dataset.expand));
    render();
  }
  if (b.dataset.repliesMore) {
    const c = db.comments.find((c) => c.id === Number(b.dataset.repliesMore));
    c.replyPage = (c.replyPage || 1) + 1;
    render();
  }
  if (b.hasAttribute("data-comments-more")) {
    page++;
    render();
  }
  if (b.dataset.deleteComment || b.dataset.deleteReply) {
    const main = !!b.dataset.deleteComment,
      id = Number(b.dataset.deleteComment || b.dataset.parent),
      c = db.comments.find((c) => c.id === id),
      r = c?.replies.find((r) => r.id === Number(b.dataset.deleteReply));
    if ((main ? c?.author : r?.author) !== current) return;
    confirmAction(
      main ? "删除这条评论？" : "删除这条回复？",
      main
        ? "该评论及其所有回复将一并删除，包括其他用户的回复。"
        : "仅删除这条回复，其他回复将保留。",
      () => {
        if (main) db.comments = db.comments.filter((c) => c.id !== id);
        else c.replies = c.replies.filter((x) => x.id !== r.id);
        save();
        render();
        toast("已删除");
      },
      "确认删除",
    );
  }
}
