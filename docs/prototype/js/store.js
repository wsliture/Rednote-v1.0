/* 共享运行时和模拟数据访问。经典 defer 脚本只初始化一次；后端迁移时替换这里的数据访问。 */
"use strict";
const $ = (s) => document.querySelector(s);
const clone = (x) => JSON.parse(JSON.stringify(x));
let db;
try {
  db = JSON.parse(localStorage.getItem("rednote-prototype-v1"));
} catch {}
if (!db || !db.users || !db.notes) db = clone(RednoteSeed);
// 兼容此前保存在浏览器中的演示数据，保留用户编辑及自行上传的图片。
const bundledImages = new Set(RednoteSeed.notes.flatMap((n) => n.images));
let migratedImages = false;
db.notes.forEach((n) => {
  n.images = n.images.map((src) => {
    const match =
      /^https:\/\/images\.unsplash\.com\/(photo-\d+-[a-z0-9]+)(?:[?].*)?$/.exec(
        src,
      );
    const local = match ? `images/${match[1]}.jpg` : src;
    if (match && bundledImages.has(local)) {
      migratedImages = true;
      return local;
    }
    return src;
  });
});
if (migratedImages) {
  try {
    localStorage.setItem("rednote-prototype-v1", JSON.stringify(db));
  } catch {}
}

let current = Number(sessionStorage.getItem("rn-user")) || 0,
  likes = JSON.parse(localStorage.getItem("rn-likes") || "{}"),
  mode = "normal",
  limit = 20,
  page = 1,
  reply = null,
  slide = 0,
  settingsTab = "profile",
  draft = null,
  dirty = false,
  returnTo = "/",
  filter = { username: "", name: "", status: "" },
  expanded = new Set(),
  previousHash = location.hash || "#/",
  allowNav = false;
const passwords = new Map(),
  commentDrafts = new Map();
const app = $("#app");
const user = (id) => db.users.find((u) => u.id === Number(id));
const me = () => user(current);
const note = (id) => db.notes.find((n) => n.id === Number(id));
const route = () => decodeURIComponent((location.hash || "#/").slice(1));

function save() {
  try {
    localStorage.setItem("rednote-prototype-v1", JSON.stringify(db));
    localStorage.setItem("rn-likes", JSON.stringify(likes));
  } catch {
    toast("本地存储空间不足，当前操作仅在本次演示保留");
  }
}

function login(id) {
  current = id;
  sessionStorage.setItem("rn-user", String(id));
}

let viewEpoch = 0,
  feedScrollPending = false;
const pageTimers = new Set();
function later(fn, ms) {
  const epoch = viewEpoch;
  const timer = setTimeout(() => {
    pageTimers.delete(timer);
    if (epoch === viewEpoch) fn();
  }, ms);
  pageTimers.add(timer);
  return timer;
}
function cancelPageWork() {
  viewEpoch++;
  feedScrollPending = false;
  pageTimers.forEach(clearTimeout);
  pageTimers.clear();
}
function listNotes(q = "", author) {
  return db.notes
    .filter(
      (n) =>
        (author === undefined || n.author === author) &&
        `${n.title} ${n.body}`.toLowerCase().includes(q.toLowerCase()),
    )
    .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
}
function listUsers(criteria) {
  return db.users
    .filter(
      (u) =>
        u.username.toLowerCase().includes(criteria.username.toLowerCase()) &&
        u.name.toLowerCase().includes(criteria.name.toLowerCase()) &&
        (!criteria.status || u.status === criteria.status),
    )
    .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
}
function nav(path) {
  location.hash = path;
}

function requireLogin() {
  if (me()?.status === "禁用") {
    login(0);
    toast("账号已禁用，已退出登录");
  }
  if (!me()) {
    returnTo = route();
    nav("/login");
    return false;
  }
  return true;
}
