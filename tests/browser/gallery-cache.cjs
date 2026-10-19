/* Isolated gallery regression; no database or application server required. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const { chromium } = require('playwright');
const moduleText = fs.readFileSync('src/main/resources/static/js/gallery.js', 'utf8');
const fixture = `<!doctype html><section data-detail><div class="gallery">
<img src="/image/0" alt="test" data-gallery-image><div hidden data-gallery-sources>
${[0,1,2].map(i=>`<span data-src="/image/${i}"></span>`).join('')}</div>
<div class="gallery-controls"><button data-slide="-1">prev</button><span data-counter>1 / 3</span><button data-slide="1">next</button></div>
</div></section><script type="module">import {initGallery} from '/gallery.js';
const move = initGallery(document.querySelector('[data-detail]'));
document.querySelectorAll('[data-slide]').forEach(b => b.onclick=()=>move(Number(b.dataset.slide)));
window.ready = true;</script>`;
const server = http.createServer((req,res)=> {
  res.setHeader('Content-Type', req.url === '/gallery.js' ? 'text/javascript' : 'text/html');
  res.end(req.url === '/gallery.js' ? moduleText : req.url === '/single'
    ? fixture.replace(/<span data-src="\/image\/[12]"><\/span>/g, '').replace('1 / 3', '1 / 1') : fixture);
});
(async()=> {
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const browser = await chromium.launch({headless:true, executablePath: process.env.REDNOTE_BROWSER_PATH || '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'});
  try {
    const page = await browser.newPage();
    const errors=[]; page.on('pageerror',e=>errors.push(e.message));
    const pending = new Map(), counts = new Map();
    let failure = null, networkFailure = false;
    await page.route('**/image/*', async route=> {
      const id=Number(route.request().url().split('/').pop());
      counts.set(id,(counts.get(id)||0)+1);
      if(id!==0) await new Promise(resolve=>pending.set(id,resolve));
      if(failure===id) {
        failure=null;
        if(networkFailure) { networkFailure=false; await route.abort(); return; }
        await route.fulfill({status:200,contentType:'image/jpeg',body:'invalid image'});
      } else await route.fulfill({contentType:'image/svg+xml',headers:{'Cache-Control':'no-store'},body:`<svg xmlns="http://www.w3.org/2000/svg" width="${100+id}" height="100"><rect width="100%" height="100%" fill="${['red','green','blue'][id]}"/></svg>`});
    });
    const base=`http://127.0.0.1:${server.address().port}`;
    const waitPending = async id => { for(let i=0; i<100 && !pending.has(id); i++) await new Promise(r=>setTimeout(r,20)); assert.ok(pending.has(id)); };
    const release = id => { pending.get(id)(); pending.delete(id); };
    const shown = async id => { await page.waitForFunction(i=>document.querySelector('[data-gallery-image]').naturalWidth===100+i,id); assert.equal(await page.locator('[data-counter]').textContent(),`${id+1} / 3`); };
    await page.goto(base,{waitUntil:'domcontentloaded'});
    await page.waitForFunction(()=>window.ready);
    await waitPending(1); await waitPending(2);
    await page.locator('[data-slide="1"]').click();
    assert.equal(await page.locator('[data-counter]').textContent(),'1 / 3');
    await page.locator('[data-slide="1"]').click();
    release(2); await shown(2);
    release(1); await page.waitForTimeout(80); await shown(2);
    await page.locator('[data-slide="1"]').click(); await shown(0);
    await page.locator('[data-slide="-1"]').click(); await shown(2);
    await page.locator('[data-slide="-1"]').click(); await shown(1);
    assert.deepEqual([...counts.values()],[1,1,1]);

    counts.clear(); failure=1;
    await page.goto(base,{waitUntil:'domcontentloaded'});
    await waitPending(1); await waitPending(2);
    await page.locator('[data-slide="1"]').click();
    release(1);
    await page.getByRole('button',{name:'重试'}).waitFor();
    await shown(0);
    await page.getByRole('button',{name:'重试'}).click();
    await waitPending(1); release(1); await shown(1);
    release(2);
    assert.equal(counts.get(1),2);
    failure=1; networkFailure=true;
    await page.goto(base,{waitUntil:'domcontentloaded'});
    await waitPending(1); await waitPending(2);
    release(1); release(2);
    await page.waitForTimeout(100);
    assert.equal(await page.getByRole('button',{name:'重试'}).count(),0);
    await page.locator('[data-slide="1"]').click();
    await waitPending(1); release(1); await shown(1);
    await page.goto(base+'/single');
    await page.waitForFunction(()=>window.ready);
    assert.equal(await page.locator('.gallery-controls').isHidden(),true);
    assert.deepEqual(errors,[]);
    console.log('Gallery regression passed: delayed loading, last click wins, wrap, node reuse, decode failure and retry.');
  } finally { await browser.close(); server.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;server.close();});
