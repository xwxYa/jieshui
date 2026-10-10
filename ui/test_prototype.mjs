import { chromium } from 'playwright';
const b = await chromium.launch();
const page = await b.newPage({ viewport: { width: 1180, height: 1000 }, deviceScaleFactor: 2 });
const errs = []; page.on('pageerror', e => errs.push(e.message));
const R = []; const ok = (n, c, x = '') => { R.push(c); console.log(`${c ? '✅' : '❌'}  ${n}${x ? '   →  ' + x : ''}`); };

const css = (sel, p) => page.locator(sel).first().evaluate((e, k) => getComputedStyle(e)[k], p);
const has = sel => page.locator(sel).count().then(n => n > 0);
const cnt = sel => page.locator(sel).count();
const step = () => page.locator('#steps li.on').getAttribute('data-s');
const boxOf = async sel => { const b = await page.locator(sel).first().boundingBox(); return b; };
const part = (t, k) => parseFloat((t.match(/matrix\([^)]*\)/) || ['matrix(1,0,0,1,0,0)'])[0].slice(7).split(',')[k]);
const sy = t => part(t, 3), sx = t => part(t, 0);

/* 相对本文件定位设计稿 —— 换机器、换目录都不用改 */
const PAGE = new URL('./prototype.html', import.meta.url).href;
const go = async () => { await page.goto('about:blank'); await page.goto(PAGE); await page.waitForTimeout(800); };

/* ══════════ 1. 登录 ══════════ */
await go();
ok('打开就是登录页', (await css('#pLogin', 'display')) === 'flex');
ok('流程板是 6 步（订单详情没了）', await cnt('#steps li') === 6, `${await cnt('#steps li')} 步`);
ok('流程指示停在「登录」', (await step()) === 'login', await step());

/* ★ 报的 bug：状态栏被一起居中，掉到屏幕中间去了 */
const scBox = await boxOf('.scr');
const sbBox = await boxOf('#pLogin .sbar');
ok('★ 登录页的状态栏贴在屏幕最顶上', Math.abs(sbBox.y - scBox.y) < 1,
  `状态栏 y=${sbBox.y.toFixed(0)} / 屏幕 y=${scBox.y.toFixed(0)}`);
/* 和别的页面对齐，说明它不是单独跑偏 */
const sbDev = await page.evaluate(() => {
  const sc = document.querySelector('.scr').getBoundingClientRect();
  return document.querySelector('#pDev .sbar').getBoundingClientRect().top - sc.top;
});
ok('★ 和别的页面的状态栏在同一高度', Math.abs((sbBox.y - scBox.y) - sbDev) < 1,
  `登录页 ${(sbBox.y - scBox.y).toFixed(0)}px / 设备页 ${sbDev.toFixed(0)}px`);

ok('★ 是验证码登录，不是密码', await cnt('#iCode') === 1 && await cnt('#iPass') === 0,
  `验证码 ${await cnt('#iCode')} 个 / 密码 ${await cnt('#iPass')} 个`);
ok('有「发送验证码」按钮', await page.locator('#bSend').textContent() === '发送验证码');

/* 扫码页原来是底部弹层，改整页后忘了补状态栏 —— 加一条通用断言盖住这类漏子 */
await page.evaluate(() => {
  ['pDev', 'pOrder', 'pWater', 'pLogin', 'pScan', 'pSummary'].forEach(id => {
    const el = document.getElementById(id);
    el.classList.remove('hide');        /* .hide 是类规则，用 style.display='' 盖不住 */
    el.classList.remove('gone');
    el.style.display = '';
    el.style.visibility = 'visible';
  });
});
const bars = await page.evaluate(() => {
  const top = document.querySelector('.scr').getBoundingClientRect().top;
  return ['pDev', 'pOrder', 'pWater', 'pLogin', 'pScan', 'pSummary'].map(id => {
    const b = document.querySelector('#' + id + ' .sbar');
    return { id, dy: b ? Math.round(b.getBoundingClientRect().top - top) : null };
  });
});
ok('★ 六个页面都有状态栏，而且都贴顶',
  bars.every(b => b.dy === 0), bars.map(b => b.id + ':' + (b.dy === null ? '没有' : b.dy + 'px')).join(' '));
await page.reload();
await page.waitForTimeout(700);

await page.screenshot({ path: '_v12-1-login.png' });

await page.locator('#bSend').click();
await page.waitForTimeout(150);
const sendTxt = await page.locator('#bSend').textContent();
ok('★ 点发送后立刻变成 60 秒倒计时', sendTxt === '60s 后重发', sendTxt);
ok('★ 倒计时期间不能重复点', await page.locator('#bSend').isDisabled());
await page.waitForTimeout(1200);
const sendTxt2 = await page.locator('#bSend').textContent();
ok('★ 一秒后真的在往下数', /5[89]s 后重发/.test(sendTxt2), sendTxt2);
await page.waitForTimeout(900);
ok('★ 验证码自动填上了（演示用）', (await page.locator('#iCode').inputValue()) === '888888',
  await page.locator('#iCode').inputValue());

await page.locator('#bLogin').click();
await page.waitForTimeout(220);
ok('★ 点登录后按钮变成「登录中…」',
  (await page.locator('#bLogin .lb').textContent()).includes('登录中'));
await page.waitForTimeout(1500);
ok('★ 登录页退场了', (await css('#pLogin', 'display')) === 'none');
await page.waitForTimeout(700);
ok('★ 两张卡片都错落进场了', await cnt('.dcard.in') === 2, `${await cnt('.dcard.in')} 张`);
ok('流程指示走到「设备列表」', (await step()) === 'dev', await step());
await page.screenshot({ path: '_v12-2-dev.png' });

/* ══════════ 2. 扫码：从右上角的「+」长大 ══════════ */
const addBox = await boxOf('#bAdd');
await page.locator('#bAdd').click();
await page.waitForTimeout(60);
const df = await page.evaluate(() => {
  const sc = document.querySelector('.scr').getBoundingClientRect();
  const el = document.getElementById('bAdd').getBoundingClientRect();
  const f = JSON.parse(document.getElementById('magic').dataset.from);
  return { dl: Math.abs(f.left - (el.left - sc.left)), dt: Math.abs(f.top - (el.top - sc.top)),
           dw: Math.abs(f.w - el.width), dh: Math.abs(f.h - el.height) };
});
ok('★ 魔板起点就是右上角那个「+」',
  df.dl < .6 && df.dt < .6 && df.dw < .6 && df.dh < .6,
  `Δ ${df.dl.toFixed(2)} / ${df.dt.toFixed(2)} / ${df.dw.toFixed(2)} / ${df.dh.toFixed(2)}`);
await page.waitForTimeout(150);
const growBox = await boxOf('#magic');
ok('★ 魔板正在从那个小圆长大', growBox.width > addBox.width + 20,
  `${Math.round(addBox.width)} → ${Math.round(growBox.width)}px`);
await page.waitForTimeout(500);
ok('★ 扫码页露出来了', (await css('#pScan', 'visibility')) === 'visible');
ok('★ 内容淡入完成', parseFloat(await css('#pScan .scanwrap', 'opacity')) > .9,
  await css('#pScan .scanwrap', 'opacity'));
ok('★ 那行「约 1.6 秒后自动扫到」已经删掉',
  !(await page.locator('#pScan').textContent()).includes('1.6'), '已删');
ok('★ 取景框还在，扫描线在来回走',
  (await css('.vf .sline', 'animationName')).includes('sweep'), await css('.vf .sline', 'animationName'));
ok('流程指示走到「扫码加设备」', (await step()) === 'scan', await step());
await page.screenshot({ path: '_v12-3-scan.png' });

await page.waitForTimeout(1600);
ok('★ 扫到了', await has('#pScan.done'));
ok('★ 扫描线停下了', (await css('.vf .sline', 'opacity')) === '0');
const bBtn = await page.locator('#bAddDev').boundingBox();
const bSf  = await page.locator('.sfound').boundingBox();
ok('★ 添加按钮是通栏的（圆角不切字）', Math.abs(bBtn.width - (bSf.width - 40)) < 2,
  `按钮 ${Math.round(bBtn.width)}px / 可用 ${Math.round(bSf.width - 40)}px`);
await page.screenshot({ path: '_v12-3b-found.png' });

await page.locator('#bAddDev').click();
await page.waitForTimeout(900);
ok('★ 加完设备回到列表（扫码页收起来了）', (await css('#pScan', 'visibility')) === 'hidden');
ok('★ 列表里多了一台设备', await cnt('.dcard') === 3, `${await cnt('.dcard')} 台`);

/* ══════════ 3. 接水 ══════════ */
await page.locator('.dcard[data-d="2"]').click();
await page.waitForTimeout(500);
ok('★ 点新设备进了接水页', (await css('#pWater', 'visibility')) === 'visible');
ok('★ 顶上显示的是新设备名',
  (await page.locator('#mDev').textContent()) === '1号楼南热水');
ok('流程指示走到「接水」', (await step()) === 'water', await step());

await page.locator('#bUnlock').click();
await page.waitForTimeout(1700);
ok('★ 解锁动画演完了', await has('.stagewrap.done'));
await page.screenshot({ path: '_v12-4-water.png' });
await page.locator('#bEnd').click();
await page.waitForTimeout(700);
await page.locator('#bk').click();
await page.waitForTimeout(700);
ok('★ 返回设备页', (await css('#pDev', 'display')) === 'flex');

/* ══════════ 4. 订单：不再有详情 ══════════ */
ok('★ 订单详情那页已经删掉了', await cnt('#pDetail') === 0, `#pDetail ${await cnt('#pDetail')} 个`);
await page.locator('#pDev .tb[data-t="1"]').click();
await page.waitForTimeout(1700);
ok('★ 切到订单页', (await css('#pOrder', 'display')) === 'flex');
ok('★ 时间轴有 7 条订单', await cnt('.orow') === 7);
ok('流程指示走到「订单」', (await step()) === 'order', await step());
await page.screenshot({ path: '_v12-5-order.png' });

await page.locator('.orow').first().click();
await page.waitForTimeout(800);
const nothing = await page.evaluate(() => {
  const vis = [...document.querySelectorAll('.page')]
    .filter(p => getComputedStyle(p).visibility === 'visible' &&
                 getComputedStyle(p).display !== 'none' &&
                 p.id !== 'pOrder' && p.id !== 'pDev')
    .map(p => p.id);
  return vis;
});
ok('★ 点订单行不再弹出任何页面', nothing.length === 0, nothing.join(',') || '没有新页面');
ok('★ 订单行也不像能点的了',
  (await css('.orow', 'cursor')) !== 'pointer', await css('.orow', 'cursor'));

/* ══════════ 5. 消费汇总 ══════════ */
await page.locator('.osum').click();
await page.waitForTimeout(800);
ok('★ 汇总页打开了', (await css('#pSummary', 'visibility')) === 'visible');
ok('★ 金额从明细算出来 = 12.34', (await page.locator('#sNum').textContent()) === '12.34',
  '¥' + await page.locator('#sNum').textContent());
ok('★ 次数 = 7 次', (await page.locator('#sCnt').textContent()) === '7 次');
ok('★ 平均每次 = ¥1.76', (await page.locator('#sAvg').textContent()).includes('1.76'));
ok('★ 常去设备 = 消费最高的那台',
  (await page.locator('#sTop').textContent()) === '1号楼西热水', await page.locator('#sTop').textContent());
const topH = (await page.locator('#sTop').boundingBox()).height;
const topFs = parseFloat(await css('#sTop', 'fontSize'));
ok('★ 设备名是一行（没被折成两行）', topH < topFs * 1.45, `高 ${topH.toFixed(1)}px / 字号 ${topFs}px`);
ok('★ 排行第一 = ¥10.59', (await page.locator('.rrow').first().textContent()).includes('10.59'));
const barY = sy(await css('.mbars > i', 'transform'));
const rankX = sx(await css('.rrow .rbar > i', 'transform'));
ok('★ 柱子和排行条都画出来了', barY > .2 && barY < .4 && Math.abs(rankX - .858) < .01,
  `柱 scaleY=${barY.toFixed(2)} · 条 scaleX=${rankX.toFixed(3)}`);
ok('流程指示走到「消费汇总」', (await step()) === 'sum', await step());
await page.screenshot({ path: '_v12-7-summary.png' });

await page.locator('#bkS').click();
await page.waitForTimeout(700);
ok('★ 汇总页收起来，回到订单页', (await css('#pSummary', 'visibility')) === 'hidden');
ok('全程无 JS 报错', errs.length === 0, errs.join(' | ').slice(0, 160));

/* ══════════ 6. 自动演一遍 ══════════ */
console.log('\n--- 自动演示（约 24 秒）---');
errs.length = 0;
await page.goto('about:blank');
await page.goto(PAGE + '#auto');
await page.waitForTimeout(1500);
ok('★ 自动演示启动了', (await page.locator('#bAuto').textContent()) === '停止');
await page.waitForTimeout(24000);
const visited = await page.evaluate(() =>
  [...document.querySelectorAll('#steps li')].map(li => li.dataset.s + ':' + (li.dataset.visited || '0')));
ok('★ 六个步骤全都走到了', visited.every(v => v.endsWith(':1')), visited.join(' '));
ok('★ 演完按钮变回来了', (await page.locator('#bAuto').textContent()) === '自动演一遍');
ok('自动演示全程无 JS 报错', errs.length === 0, errs.join(' | ').slice(0, 160));

console.log(`\n通过 ${R.filter(Boolean).length} / ${R.length}`);
await b.close();
