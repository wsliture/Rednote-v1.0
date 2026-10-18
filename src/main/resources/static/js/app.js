import { imageErrors, toast } from "./ui.js";
import { counters, length } from "./validation.js";
import { initForms } from "./pages/forms.js";
import { initFeed } from "./pages/feed.js";
import { initNote } from "./pages/note.js";
import { handleAdmin } from "./pages/admin.js";
import { handleLike } from "./likes.js";
imageErrors();
counters();
initForms();
initFeed();
const handleNote = initNote();
// One delegated listener also handles newly appended server-rendered fragments.
document.addEventListener("click", async (event) => {
  const button = event.target.closest("button");
  if (!button) return;
  try {
    await handleLike(button);
    await handleNote(button);
    await handleAdmin(button);
  } catch (error) {
    toast(error.message);
  }
});

for (const form of document.querySelectorAll("form.search")) {
  const input = form.elements.q;
  input.addEventListener("input", () => input.setCustomValidity(""));
  form.addEventListener("submit", (event) => {
    if (length(input.value.trim()) > 100) {
      event.preventDefault();
      input.setCustomValidity("搜索关键词最多 100 字，请修改后重试");
      input.reportValidity();
    }
  });
}
