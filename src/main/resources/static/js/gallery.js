// Keep decoded image nodes for the lifetime of this detail page.
export function initGallery(detail) {
  const gallery = detail.querySelector('.gallery');
  const sources = [...detail.querySelectorAll('[data-gallery-sources] [data-src]')]
    .map((el) => el.dataset.src);
  const counter = detail.querySelector('[data-counter]');
  const controls = detail.querySelector('.gallery-controls');
  controls.hidden = sources.length < 2;
  const status = document.createElement('div');
  status.className = 'gallery-status';
  status.setAttribute('role', 'status');
  gallery.append(status);
  let current = gallery.querySelector('[data-gallery-image]');
  let shown = 0, target = 0, revision = 0;
  const cache = new Map();

  function load(index, initial = false) {
    const src = sources[index];
    if (cache.has(src)) return cache.get(src);
    const img = initial ? current : new Image();
    img.alt = current.alt;
    img.setAttribute('data-gallery-image', '');
    const promise = new Promise((resolve, reject) => {
      const cleanup = () => {
        img.removeEventListener('load', ready);
        img.removeEventListener('error', failed);
      };
      const failed = () => { cleanup(); reject(new Error('图片暂时无法加载')); };
      const ready = async () => {
        cleanup();
        try {
          await img.decode();
          resolve(img);
        } catch (error) { reject(error); }
      };
      img.addEventListener('load', ready);
      img.addEventListener('error', failed);
      if (!initial) img.src = src;
      else if (img.complete) img.naturalWidth ? ready() : failed();
    });
    cache.set(src, promise);
    promise.catch(() => { if (cache.get(src) === promise) cache.delete(src); });
    return promise;
  }
  function preload(index) {
    if (sources.length < 2) return;
    for (const next of [(index + 1) % sources.length,
      (index - 1 + sources.length) % sources.length]) {
      if (next !== index) load(next).catch(() => {});
    }
  }
  async function display(index, initial = false) {
    const ticket = ++revision;
    gallery.setAttribute('aria-busy', 'true');
    status.textContent = '图片加载中…';
    try {
      const img = await load(index, initial);
      if (ticket !== revision) return;
      if (img !== current) current.replaceWith(img);
      current = img;
      shown = index;
      counter.textContent = `${shown + 1} / ${sources.length}`;
      status.replaceChildren();
      preload(shown);
    } catch (error) {
      if (ticket !== revision) return;
      status.textContent = '图片暂时无法加载 ';
      const retry = document.createElement('button');
      retry.type = 'button';
      retry.textContent = '重试';
      retry.onclick = () => display(index);
      status.append(retry);
    } finally {
      if (ticket === revision) gallery.setAttribute('aria-busy', 'false');
    }
  }
  if (sources.length) display(0, true);
  return (delta) => {
    if (sources.length < 2) return;
    target = (target + delta + sources.length) % sources.length;
    display(target);
  };
}
