import { chromium } from 'playwright';

/* 相对本文件定位设计稿 —— 换机器、换目录都不用改 */
const PAGE = new URL('./prototype.html', import.meta.url).href;

const errors = [];
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1240, height: 960 } });
page.setDefaultTimeout(4000);
page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));

await page.goto(PAGE + '#dev');
await page.waitForTimeout(600);

const R = [];
const ok = (n, c, x = '') => { R.push({ name: n, pass: !!c, extra: x });
  console.log(`${c ? '✅' : '❌'}  ${n}${x ? '   →  ' + x : ''}`); };
const css = (s, p) => page.locator(s).first().evaluate((e, p) => getComputedStyle(e)[p], p);
const attr = (s, a) => page.locator(s).first().getAttribute(a);
const has = async (s, t) => (await attr(s, 'class') || '').includes(t);

/* ═══════ 设备页 ═══════ */
ok('两张设备卡片', await page.locator('.dcard').count() === 2);
await page.waitForTimeout(400);
ok('卡片依次滑入', await page.locator('.dcard.in').count() === 2);
ok('设备页在', !(await has('#pDev', 'hide')));
ok('接水页藏着', !(await has('#pWater', 'on')));

/* ═══════ 底部 tab ═══════ */
ok('设备页有两个 tab', await page.locator('#pDev .tb').count() === 2);
ok('默认选中设备', (await page.locator('#pDev .tb').first().getAttribute('class')).includes('on'));
await page.locator('#pDev .tb').nth(1).click();
await page.waitForTimeout(200);
ok('★ 点订单切到订单页', !(await has('#pOrder', 'hide')));
ok('★ 设备页藏起来了', await has('#pDev', 'hide'));
await page.locator('#pOrder .tb').first().click();
await page.waitForTimeout(200);
ok('★ 切回设备页', !(await has('#pDev', 'hide')));

/* ═══════ 进接水页(卡片扩展) ═══════ */
const card0 = page.locator('.dcard').first();
const cr = await card0.boundingBox();
await card0.click();
await page.waitForTimeout(70);
ok('★ 替身出现了', (await css('#magic', 'display')) === 'block');
const scRect = await page.locator('.scr').first().boundingBox();
const from = JSON.parse(await page.locator('#magic').getAttribute('data-from'));
ok('★ 替身起点 = 卡片位置',
   Math.abs(from.left + scRect.x - cr.x) < 2 && Math.abs(from.top + scRect.y - cr.y) < 2,
   `rel ${Math.round(from.left)},${Math.round(from.top)} vs ${Math.round(cr.x - scRect.x)},${Math.round(cr.y - scRect.y)}`);
ok('★ 替身尺寸 = 卡片尺寸', Math.abs(from.w - cr.width) < 2 && Math.abs(from.h - cr.height) < 2,
   `${Math.round(from.w)}x${Math.round(from.h)} vs ${Math.round(cr.width)}x${Math.round(cr.height)}`);
const mR = parseFloat(await css('#magic', 'borderRadius'));
ok('★ 圆角在收起(卡片 18px → 全屏 0)', mR > 0 && mR < 17, mR.toFixed(1) + 'px');
ok('★ 替身里装着卡片克隆', await page.locator('#magic .dcard').count() === 1);
ok('★ 替身底色 = 卡片本色(它就是一个放大的卡片)',
   (await css('#magic', 'backgroundColor')) === (await css('#magic .dcard', 'backgroundColor')) ||
   (await css('#magic', 'backgroundColor')) === 'rgb(255, 255, 255)',
   await css('#magic', 'backgroundColor'));

await page.waitForTimeout(100);
const m1 = await page.locator('#magic').boundingBox();
ok('★ 替身正在长大', m1.width > cr.width * 1.05, `${Math.round(cr.width)} → ${Math.round(m1.width)}`);
ok('★ 卡片上的字已经淡出(0~25% 进度内让位)',
   parseFloat(await css('#magic .dcard', 'opacity')) < .3,
   await css('#magic .dcard', 'opacity'));
ok('★ 背景压暗到 32%(scrim)', Math.abs(parseFloat(await css('#scrim', 'opacity')) - .32) < .02,
   await css('#scrim', 'opacity'));
ok('★ 缓动是 Material 官方那条',
   (await css('#magic', 'transitionTimingFunction')).startsWith('cubic-bezier(0.4, 0, 0.2, 1)'),
   await css('#magic', 'transitionTimingFunction'));
ok('★ 时长 300ms', (await css('#magic', 'transitionDuration')).startsWith('0.3s'),
   await css('#magic', 'transitionDuration'));

await page.waitForTimeout(280);
ok('★ 接水页露出来了', await has('#pWater', 'on'));
ok('★ 内容是淡入的(0.3s,不是整页直接蹦出来)',
   (await css('#pWater .msg', 'transitionProperty')) === 'opacity' &&
   (await css('#pWater .msg', 'transitionDuration')) === '0.3s',
   await css('#pWater .msg', 'transitionDuration'));
await page.waitForTimeout(700);
ok('★ 内容落位了', parseFloat(await css('#pWater .msg', 'opacity')) > .95,
   await css('#pWater .msg', 'opacity'));
ok('★ 没有遮罩层了', await page.locator('.wipe').count() === 0);
await page.waitForTimeout(420);
ok('★ 替身收工了', (await css('#magic', 'display')) === 'none');

/* ═══════ 锁定态 ═══════ */
ok('★ 锁体是灰的', (await css('.lbody', 'fill')) === 'rgb(195, 175, 169)', await css('.lbody', 'fill'));
ok('★ 圆环是点线', (await css('.ring .dots', 'strokeDasharray')) === '0.5px, 12.745px',
   await css('.ring .dots', 'strokeDasharray'));
ok('★ 点线在慢慢转', (await css('.ring .dots', 'animationName')) === 'dotspin' &&
                    (await css('.ring .dots', 'animationDuration')) === '22s',
   await css('.ring .dots', 'animationDuration'));
ok('★ 进度环是隐藏的', (await css('.ring .prog', 'opacity')) === '0');
ok('★ 亮点没出现', (await css('.orbit', 'opacity')) === '0');
ok('★ 睡着:眼睛是弧线', (await css('.lock .eyeline', 'opacity')) === '1');
ok('★ 睡着:眼珠不显示', (await css('.lock .eyedot', 'opacity')) === '0');
ok('★ 装饰点还没出现', (await css('.ring .pip', 'opacity')) === '0');
ok('★ 背光还没亮', !(await has('#halo', 'lit')));
ok('文字是「设备已锁定」', (await page.locator('#mBig').textContent()).includes('设备已锁定'));
ok('结束按钮禁用', await page.locator('#bEnd').isDisabled());

/* ═══════ 点解锁 —— 时间轴前半段 ═══════ */
await page.locator('#bUnlock').click();
await page.waitForTimeout(60);
ok('按钮在换字', await has('#bUnlock', 'swapping'));
ok('按钮已禁用', await page.locator('#bUnlock').isDisabled());

await page.waitForTimeout(170);            /* → 230ms */
ok('★ 锁在抖', (await css('.lock', 'animationName')) === 'shake', await css('.lock', 'animationName'));
ok('★ 眼睛睁开了(弧线隐)', (await css('.lock .eyeline', 'opacity')) === '0');
ok('★ 眼珠出现了', (await css('.lock .eyedot', 'opacity')) === '1');
ok('★ 圆环转速提上来了', (await css('.ring .dots', 'animationDuration')) === '2.2s',
   await css('.ring .dots', 'animationDuration'));

await page.waitForTimeout(170);            /* → 400ms  扫过曲线前段很慢,这里才刚开始动 */
ok('★ 进度环开始画了', (await css('.ring .prog', 'opacity')) === '1');
const dash = await css('.ring .prog', 'strokeDasharray');
const dashHead = parseFloat(dash.split(',')[0]);
ok('★ 进度环正在生长', dashHead > 0 && dashHead < 490, dash);
ok('★ 亮点在跑', (await css('.orbit', 'animationName')) === 'orbitSpin', await css('.orbit', 'animationName'));

await page.waitForTimeout(170);            /* → 570ms  颜色过渡 .5s 已完成 */
ok('★ 锁变主题色', (await css('.lbody', 'fill')) === 'rgb(143, 76, 56)', await css('.lbody', 'fill'));

/* ═══════ 后半段:弹开 + 炸开 ═══════ */
await page.waitForTimeout(1010);
ok('★ 进入 done', await has('#wrap', 'done'));
ok('★ 锁梁弹开了', parseFloat(await css('.lock .shackle', 'transform').then(t => {
      const m = t.match(/matrix\(([^)]+)\)/); return m ? m[1].split(',')[1] : '0';
    })) < -0.4, await css('.lock .shackle', 'transform'));
ok('★ 锁做了果冻', (await css('.lock', 'animationName')).includes('jelly'), await css('.lock', 'animationName'));
ok('★ 进度环画满了', parseFloat((await css('.ring .prog', 'strokeDasharray')).split(',')[0]) > 489,
   await css('.ring .prog', 'strokeDasharray'));
ok('★ 爆开了', await has('#burst', 'go'));
ok('★ 两圈冲击波', await page.locator('.burst .wave').count() === 2);
ok('★ 八道放射线', await page.locator('.burst .ray').count() === 8);
const rays = await page.locator('.burst .ray').evaluateAll(els => els.map(e => ({
  a: e.style.getPropertyValue('--a'), dl: e.style.getPropertyValue('--dl')
})));
ok('★ 每道线方向不同', new Set(rays.map(r => r.a)).size === 8);
ok('★ 延迟错开', new Set(rays.map(r => r.dl)).size === 8, rays.map(r => r.dl).slice(0, 3).join(' '));
ok('★ 是细线不是圆点', parseFloat(await css('.burst .ray', 'width')) < 4, await css('.burst .ray', 'width'));
ok('★ 冲击波动画在跑', (await css('.burst .wave', 'animationName')) === 'wave');
ok('★ 放射线动画在跑', (await css('.burst .ray', 'animationName')) === 'ray');

await page.waitForTimeout(400);
const dropPath = await css('.lbody', 'd');
ok('★ 锁体长成了水滴(尖端在 y=18)', dropPath.includes('38 18') || dropPath.includes('38,18'),
   dropPath.slice(0, 60));
ok('★ 水滴是瘦长的(不再是矮胖)', dropPath.includes('38 90'), dropPath.slice(-42));
ok('★ 水滴变蓝了', (await css('.lbody', 'fill')) === 'rgb(95, 182, 224)',
   await css('.lbody', 'fill'));
ok('★ 水滴在呼吸', (await css('.lock', 'animationName')).includes('breathe'),
   await css('.lock', 'animationName'));
ok('★ 锁梁飘走了', parseFloat(await css('.lock .shackle', 'opacity')) < .1,
   await css('.lock .shackle', 'opacity'));
ok('★ 环上装饰点出现了', parseFloat(await css('.ring .pip', 'opacity')) > .3,
   await css('.ring .pip', 'opacity'));
ok('★ 装饰点延迟错开', (await css('.ring .pip.p2', 'animationDelay')) === '0.42s',
   await css('.ring .pip.p2', 'animationDelay'));
ok('★ 锁在分段眨眼', !(await css('.lock .eyedot', 'animationIterationCount')).includes('infinite'),
   await css('.lock .eyedot', 'animationIterationCount'));
ok('★ 锁会左右瞟', (await css('.lface', 'animationName')) === 'lockLook', await css('.lface', 'animationName'));
ok('★ 背光亮起来了', await has('#halo', 'lit'));
ok('★ 文字换成了「已解锁」', (await page.locator('#mBig').textContent()).includes('已解锁'));
ok('★ 提示去机器上按按钮', (await page.locator('#mSm').textContent()).includes('机器上按接水按钮'));
ok('解锁 toast 弹了', await has('#toast', 'on'));
ok('★ toast 是挤压弹出', (await css('#toast', 'animationName')) === 'toastPop');
ok('结束按钮能点了', !(await page.locator('#bEnd').isDisabled()));

/* ═══════ 结束 ═══════ */
await page.locator('#bEnd').click();
await page.waitForTimeout(950);
ok('★ 结束回到锁定态', !(await has('#wrap', 'done')) && !(await has('#wrap', 'waking')));
ok('★ 锁又变灰了', (await css('.lbody', 'fill')) === 'rgb(195, 175, 169)');
ok('★ 眼睛又闭上了', (await css('.lock .eyeline', 'opacity')) === '1');
ok('★ 结束能重复', !(await page.locator('#bUnlock').isDisabled()));

/* ═══════ 返回 + 离线设备 ═══════ */
await page.locator('#bk').click();
await page.waitForTimeout(50);
ok('★ 返回:点下去就切页了(不再有 420ms 延迟)', !(await has('#pDev', 'hide')));
ok('★ 返回:接水页同时收起来了', !(await has('#pWater', 'on')));
ok('★ 返回:魔板已经铺满', (await css('#magic', 'display')) === 'block');
const mb = await page.locator('#magic').boundingBox();
ok('★ 返回:魔板从整页开始缩', mb.width > 300, String(Math.round(mb.width)));
ok('★ 返回时不压暗(设备列表要看得见)',
   parseFloat(await css('#scrim', 'opacity')) < .02, await css('#scrim', 'opacity'));
ok('★ 返回:魔板里也装着卡片(缩回去的一路都有卡片的样子)',
   await page.locator('#magic .dcard').count() === 1);
await page.waitForTimeout(900);
ok('★ 返回:魔板收工', (await css('#magic', 'display')) === 'none');
ok('★ 返回:遮罩也收干净了', parseFloat(await css('#scrim', 'opacity')) < .02,
   await css('#scrim', 'opacity'));
ok('★ 返回后设备页稳了', !(await has('#pDev', 'hide')));
await page.locator('.dcard').nth(1).click();
await page.waitForTimeout(1400);
ok('离线设备也能进', !(await has('#pWater', 'hide')));
await page.locator('#bUnlock').click();
await page.waitForTimeout(300);
ok('★ 离线设备被拦下', (await page.locator('#toast').textContent()).includes('离线'));
ok('★ 离线时锁不动', !(await has('#wrap', 'waking')) && !(await has('#wrap', 'done')));

/* ═══════ 只动 transform / opacity ═══════ */
const perf = await page.evaluate(() => {
  const bad = [];
  for (const el of document.querySelectorAll('.ring circle, .lock *, .orbit, .burst i, .pip')) {
    const t = getComputedStyle(el).transitionProperty;
    if (t && t !== 'none' && /(^|,)\s*(width|height|top|left|margin|padding)/.test(t)) {
      bad.push(el.className + ':' + t);
    }
  }
  return bad;
});
ok('★ 只动 transform/opacity,没碰会重排的属性', perf.length === 0, perf.join(' | '));

ok('全程无 JS 报错', errors.length === 0, errors.join(' | '));

console.log('\n══════════ v9 · 会醒来的锁 ══════════');
let pass = 0, fail = 0;
for (const r of R) { console.log(`${r.pass ? '✅' : '❌'}  ${r.name}${r.extra ? '   →  ' + r.extra : ''}`); r.pass ? pass++ : fail++; }
console.log(`\n${fail === 0 ? '🎉' : '⚠️'}  通过 ${pass} / ${pass + fail}`);
if (errors.length) { console.log('\nJS 错误:'); errors.forEach(e => console.log('   ' + e)); }

await browser.close();
process.exit(fail ? 1 : 0);
