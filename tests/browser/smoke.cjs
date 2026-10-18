/* Real persistence regression. Run only against an isolated validation database. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { chromium } = require('playwright');
const base = process.env.REDNOTE_URL || 'http://localhost:8085';
let browser;
(async () => {
  browser = await chromium.launch({headless:true, executablePath:process.env.REDNOTE_BROWSER_PATH || '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge'});
  const p = await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[]; p.on('pageerror',e=>errors.push(e.message));
  const username='browser_'+crypto.randomBytes(4).toString('hex'), password=crypto.randomBytes(10).toString('hex')+'a9';
  const csrf=async()=>p.locator('meta[name="csrf-token"]').getAttribute('content');
  const login=async(user,pass)=>{
    await p.goto(base+'/login');await p.locator('#username').fill(user);await p.locator('#password').fill(pass);
    await Promise.all([p.waitForURL(base+'/'),p.locator('[data-auth] .primary').click()]);
  };
  const guestHealth=await p.request.get(base+'/actuator/health');assert.equal(guestHealth.status(),200);assert.equal((await guestHealth.json()).components,undefined);
  await p.goto(base+'/register');await p.locator('#username').fill(username);await p.locator('#password').fill(password);await p.locator('#confirm').fill(password);
  await Promise.all([p.waitForURL(/\/login\?registered/),p.locator('[data-auth] .primary').click()]);
  await login(username,password);
  const headers={'X-CSRF-TOKEN':await csrf()};
  const image=fs.readFileSync(path.resolve('src/main/resources/static/images/photo-1441974231531-c6227db76b6e.jpg'));
  const uploaded=await p.request.post(base+'/actions/files',{headers,multipart:{file:{name:'test.jpg',mimeType:'image/jpeg',buffer:image}}});assert.equal(uploaded.status(),200,await uploaded.text());const asset=await uploaded.json();
  const own=[];
  for(let i=0;i<44;i++){
    const r=await p.request.post(base+'/notes',{headers,form:{title:username+' 山野 '+i,body:'真实数据库与本地文件\n<script>safe</script>',images:asset.id}});
    assert.equal(r.status(),200,await r.text());own.push(new URL(r.url()).pathname);
  }
  await p.goto(base+'/search?q='+username);assert.equal(await p.locator('.note-card').count(),20);
  await p.locator('[data-load-feed]').evaluate(e=>e.click());await p.waitForFunction(()=>document.querySelectorAll('.note-card').length>=40);
  await p.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight));await p.waitForFunction(()=>document.querySelectorAll('.note-card').length===44);
  assert.equal(new Set(await p.locator('.note-card').evaluateAll(e=>e.map(x=>x.dataset.noteId))).size,44);
  await p.goto(base+own[0]);assert.equal(await p.locator('.article h1').textContent(),username+' 山野 0');assert.ok(!(await p.content()).includes('<script>safe</script>'));
  await p.locator('[data-like]').first().click();await p.waitForFunction(()=>document.querySelector('[data-like]').getAttribute('aria-pressed')==='true');
  await p.locator('#comment-form textarea').fill('浏览器主评论 <script>');await p.locator('#comment-form .primary').click();await p.getByText('浏览器主评论 <script>',{exact:true}).waitFor();
  await p.locator('[data-reply]').first().click();await p.locator('#comment-form textarea').fill('浏览器回复');await p.locator('#comment-form .primary').click();await p.waitForFunction(()=>document.querySelector('#comment-form textarea').value==='');
  await p.locator('[data-load-replies]').first().click();await p.getByText('浏览器回复',{exact:true}).waitFor();
  await p.getByRole('link',{name:'编辑',exact:true}).click();await p.locator('#title').fill('浏览器修改成功');
  await Promise.all([p.waitForURL(base+own[0]),p.locator('[data-editor] .primary').click()]);assert.equal(await p.locator('.article h1').textContent(),'浏览器修改成功');
  await p.locator('[data-delete-comment][data-root-delete="true"]').click();await p.locator('[role="dialog"] .primary').click();await p.waitForFunction(()=>document.querySelector('[data-comment-count]').textContent==='共 0 条评论');
  await p.goto(base+'/notes/new');await p.locator('#title').fill('多格式图片发布');await p.locator('#body').fill('JPEG PNG WebP 实际解码');
  const generated=await p.evaluate(()=>{const c=document.createElement('canvas');c.width=2;c.height=2;c.getContext('2d').fillRect(0,0,2,2);return {png:c.toDataURL('image/png').split(',')[1],webp:c.toDataURL('image/webp').split(',')[1]};});
  await p.route('**/actions/files',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'模拟单张上传失败'})}),{times:1});
  await p.locator('#images').setInputFiles({name:'retry.png',mimeType:'image/png',buffer:Buffer.from(generated.png,'base64')});
  await p.locator('.upload-item').getByRole('button',{name:'重试'}).waitFor();await p.locator('.upload-item').getByRole('button',{name:'重试'}).click();
  await p.waitForFunction(()=>document.querySelectorAll('input[name="images"]').length===1);
  await p.locator('#images').setInputFiles([{name:'tiny.png',mimeType:'image/png',buffer:Buffer.from(generated.png,'base64')},{name:'tiny.webp',mimeType:'image/webp',buffer:Buffer.from(generated.webp,'base64')}]);
  await p.waitForFunction(()=>document.querySelectorAll('input[name="images"]').length===3);
  await p.route(base+'/notes',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'模拟保存失败，保留输入'})}),{times:1});
  await p.locator('[data-editor] .primary').click();await p.getByText('模拟保存失败，保留输入',{exact:true}).waitFor();assert.equal(await p.locator('#title').inputValue(),'多格式图片发布');assert.equal(await p.locator('input[name="images"]').count(),3);
  await Promise.all([p.waitForURL(/\/notes\/\d+$/),p.locator('[data-editor] .primary').click()]);
  const created=new URL(p.url()).pathname;
  await p.locator('[data-delete-note]').click();await p.locator('[role="dialog"] .primary').click();await p.waitForURL(base+'/');assert.equal((await p.request.get(base+created)).status(),404);
  await p.goto(base+'/settings');await p.locator('#name').fill('持久化浏览器用户');await p.locator('#bio').fill('真实资料');
  await Promise.all([p.waitForURL(/\/users\/\d+$/),p.locator('[data-profile] .primary').click()]);const userPath=new URL(p.url()).pathname;
  for(const width of [1440,375]){
    await p.setViewportSize({width,height:1000});
    for(const route of ['/', '/search?q='+username,own[0],own[0]+'/edit','/notes/new',userPath,'/settings','/login','/register']){
      const r=await p.goto(base+route);assert.equal(r.status(),200,route);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,width+' '+route);
    }
  }
  fs.mkdirSync('target',{recursive:true});fs.writeFileSync('target/browser-fixture.json',JSON.stringify({username,password,notePath:own[0],userPath,asset:asset.id}));
  if(process.env.REDNOTE_ADMIN_USERNAME&&process.env.REDNOTE_ADMIN_PASSWORD){
    await p.goto(base+'/settings');await p.locator('[data-logout] button').click();await p.locator('[role="dialog"] .primary').click();await p.waitForURL(base+'/');
    await login(process.env.REDNOTE_ADMIN_USERNAME,process.env.REDNOTE_ADMIN_PASSWORD);
    const health=await p.request.get(base+'/actuator/health');assert.equal(health.status(),200);assert.ok((await health.json()).components.uploadStorage);
    for(const width of [1440,375]){await p.setViewportSize({width,height:1000});for(const route of ['/admin','/admin/users']){assert.equal((await p.goto(base+route)).status(),200);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);}}
    await p.goto(base+'/admin/users?username='+username);const id=userPath.split('/').at(-1);await p.locator('[data-user-detail="'+id+'"]').click();await p.locator('[role="dialog"] dl').waitFor();await p.keyboard.press('Escape');
    await p.locator('[data-user-status="'+id+'"]').click();await p.locator('[role="dialog"] .primary').click();await p.waitForFunction(id=>document.querySelector('[data-user-status="'+id+'"]').dataset.status==='正常',id);
    await p.locator('[data-user-status="'+id+'"]').click();await p.locator('[role="dialog"] .primary').click();await p.waitForFunction(id=>document.querySelector('[data-user-status="'+id+'"]').dataset.status==='禁用',id);
  }
  assert.deepEqual(errors,[]);console.log('PASS: real registration/login, 44-note pagination, JPEG/PNG/WebP, publish/edit/delete, likes, comments/replies, settings, admin, 1440/375 layouts');
  await browser.close();
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1;});
