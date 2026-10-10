# -*- coding: utf-8 -*-
"""从 prototype.html 生成真 App 的界面 —— app/app/src/main/assets/app.html

同一份设计稿，外观一个字不改，只做三件事：
  ① 拆掉手机壳（黑边框 / 刘海 / 右边那块说明板）—— 真机上屏幕就是屏幕
  ② 设备列表和订单改成"按数据渲染"（原来是两张写死的卡片 + 一份假订单）
  ③ 数据层接上原生桥 Android.xxx()，签名、网络、解码全在 Kotlin 那边

跑法：python _build_app.py
"""
import io, os

SRC = 'prototype.html'
DST = '../app/app/src/main/assets/app.html'

s = io.open(SRC, encoding='utf-8').read()

def rep(a, b, tag):
    global s
    assert a in s, '找不到: ' + tag
    s = s.replace(a, b, 1); print('  ok', tag)

def put(a, b, new, tag):
    """把 a 到 b 之间的东西换成 new（b 保留）。b 必须排在 a 后面。"""
    global s
    assert a in s, 'put 起点找不到: ' + tag
    i = s.index(a)
    j = s.index(b, i)
    s = s[:i] + new + s[j:]
    print('  ok', tag)

# ═══════════════════════ ① App 模式 CSS
APP_CSS = u'''
  /* ═══════════════════════════════════════════════════════════
     App 模式 —— 手机壳拆掉，屏幕铺满
     上面那套 .dev/.scr 是给电脑上并排看几个手机用的，真机上全不要。
     ═══════════════════════════════════════════════════════════ */
  html,body{ width:100%; height:100%; overflow:hidden; background:var(--bg) }
  body{ display:block; padding:0; gap:0 }
  .dev{ width:100%; height:100%; border-radius:0; padding:0;
        background:transparent; box-shadow:none }
  .scr{ border-radius:0 }
  .notch{ display:none }
  .flow{ display:none }
  /* 真机上系统状态栏是系统画的，这一条只留呼吸空间；里面的字和图标全藏掉 */
  .sbar{ height:12px; visibility:hidden }
  /* 手机上别选中文字、别弹复制菜单 —— 输入框例外 */
  *{ -webkit-user-select:none; user-select:none; -webkit-touch-callout:none }
  input{ -webkit-user-select:text; user-select:text }

  /* 一台设备都没有的时候 */
  .dempty{ margin:6px 20px; padding:38px 20px; border-radius:22px; text-align:center;
           font-size:13px; line-height:1.9; color:#A08E88; background:var(--surface) }

  /* ═══ 扫码取景框里塞真相机 ═══ */
  .vf{ background:#12100F; overflow:hidden }
  .vf .cam{ position:absolute; inset:0; width:100%; height:100%; object-fit:cover;
            display:block; background:#12100F; transition:opacity .3s linear }
  #pScan.done .vf .cam{ opacity:.2 }
  .vf .sline{ z-index:2 }
  /* 扫不出来时的兜底 */
  .mini{ margin-top:2px; border:0; background:none; font:inherit; font-size:12.5px;
         color:#A08E88; text-decoration:underline; text-underline-offset:3px;
         cursor:pointer; padding:6px 10px }
  .manual{ position:absolute; inset:0; z-index:8; display:none;
           align-items:center; justify-content:center; padding:0 26px;
           background:rgba(26,17,15,.42) }
  .manual.on{ display:flex }
  .mbox{ width:100%; background:var(--bg); border-radius:24px; padding:24px 22px 18px;
         display:flex; flex-direction:column; gap:9px }
  .mt{ font-size:16px; font-weight:700; text-align:center }
  .ms{ font-size:12px; color:#A08E88; text-align:center; line-height:1.7; margin-bottom:4px }
  .mbox input{ width:100%; border:0; border-radius:15px; background:var(--surface);
               padding:13px 16px; font:inherit; font-size:15px; font-weight:650;
               letter-spacing:.04em; color:var(--on-surface); outline:none }
'''
rep('</style>', APP_CSS + '</style>', 'App 模式 CSS')

# ═══════════════════════ ② 登录页：清干净
rep('<input id="iPhone" value="13800001234" inputmode="numeric"></label>',
    '<input id="iPhone" inputmode="numeric" placeholder="11 位手机号"></label>',
    '去掉预填的手机号')

rep('<div class="ptitle">热水</div>', '<div class="ptitle">接水</div>', '标题改成「接水」')

rep('''        <div class="psub">校园直饮水 · 扫码即用<br>不预存、不办卡，接多少算多少</div>
''', '', '去掉中间的标语')

rep('''        <div class="plegal">登录即表示同意「用户协议」和「隐私政策」<br>这只是一个原型，不会真的联网</div>
''', '', '去掉底部的说明')

# ═══════════════════════ ③ 设备列表改成空容器
put('      <div class="dlist" id="dlist">',
    '      <div class="tabbar">',
    '      <div class="dlist" id="dlist"></div>\n',
    '设备列表改成空容器')

# ═══════════════════════ ④ 扫码页：取景框塞真视频 + 手动兜底
rep('''        <div class="vf">
          <span class="c tl"></span><span class="c tr"></span>''',
    '''        <div class="vf">
          <video class="cam" id="cam" playsinline muted></video>
          <span class="c tl"></span><span class="c tr"></span>''',
    '取景框加视频层')

rep('''        <div class="shint">把机器上的二维码放进取景框</div>
      </div>''',
    '''        <div class="shint">把机器上的二维码放进取景框</div>
        <button class="mini" id="bManual" type="button">扫不出来？手动输设备号</button>
      </div>

      <div class="manual" id="manual">
        <div class="mbox">
          <div class="mt">手动输设备号</div>
          <div class="ms">机器贴纸上那串数字，一般是 15 位</div>
          <input id="iSn" inputmode="numeric" placeholder="输入机器上的 15 位编号">
          <button class="btn fill" id="bSnOk" type="button"><span class="lb">就用这台</span></button>
          <button class="btn dark" id="bSnNo" type="button"><span class="lb">算了</span></button>
        </div>
      </div>''',
    '加手动输设备号的兜底')

# 扫码结果卡片要能被填内容
rep('          <div class="i"><div class="n">1号楼南热水</div>\n' +
    '            <div class="p">0.011 元/100mL · 空闲</div></div>',
    '          <div class="i"><div class="n" id="sfName">—</div>\n' +
    '            <div class="p" id="sfSub">—</div></div>',
    '扫码结果卡片加 id')

# ═══════════════════════ ⑤ 设备数据层
rep("""const DEV = [
  { name:'1号楼西热水', off:false },
  { name:'2号楼东热水', off:true  }
];
let curDev = 0;

document.querySelectorAll('.dcard').forEach(c => {
  c.addEventListener('click', () => openWater(+c.dataset.d));
});""",
"""const ARROW = '<span class="arw"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
              'stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">' +
              '<path d="M9 6l6 6-6 6"/></svg></span>';

const esc = t => String(t == null ? '' : t)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;');

/* 机器到底空不空，这行要说真话。
   workStatus 是原生那边现问的 goods/water/sync（枚举见 docs/API.md）。 */
function devState(d){
  if(d.off) return '离线';
  switch(d.workStatus){
    case 1:  return '空闲';
    case 2:  return '使用中';
    case 3:  return '别人在用';
    default: return '状态未知';
  }
}

function deviceSub(d){
  const price = d.price ? d.price + ' 元/100mL' : '';
  const state = devState(d);
  return price ? price + ' · ' + state : state;
}

function renderDevices(){
  const box = $('dlist');
  if(!DEV.length){
    box.innerHTML = '<div class="dempty">还没有设备<br>点右上角的 + 扫机器上的二维码</div>';
    return;
  }
  box.innerHTML = DEV.map((d, i) =>
    '<div class="dcard" data-d="' + i + '">' +
      '<span class="dot' + (d.off ? ' off' : '') + '"></span>' +
      '<div class="i"><div class="n">' + esc(d.name || '未命名设备') + '</div>' +
      '<div class="p">' + esc(deviceSub(d)) + '</div></div>' + ARROW +
    '</div>').join('');
  box.querySelectorAll('.dcard').forEach(c => {
    c.addEventListener('click', () => openWater(+c.dataset.d));
  });
}""",
    '设备数据层')

# ═══════════════════════ ⑥ 订单数据层
put('const ORDERS = [', 'const DLD   =', 'let ORDERS = [];\n', '订单改成空数组')

rep("""const DLD   = [0,90,40,160,220,130,60];            /* 故意打乱的延迟，递增会像音阶 */""",
"""const DLD   = [0,90,40,160,220,130,60];            /* 故意打乱的延迟，递增会像音阶 */

/* 原生给的是扁平订单，这里先按天分组。
   分组的形状跟演示版一样，所以汇总页不用改。
   额外存一个 y（年份）—— 年份滚轮靠它分组。 */
function setOrders(list){
  const byDay = new Map();
  list.forEach(o => {
    const amt = +o.amt || 0;
    /* 0 元的订单不算 —— 接口里大量 payPrice 是 "0.00" 的（免费/已取消），
       金额上不影响求和，但会把「N 单」撑虚。只统计真花了钱的。 */
    if(amt <= 0) return;
    const key = o.dateKey || '';
    const p = key.split('-');
    const y = +p[0] || 0, mon = +p[1] || 0;
    /* 年月解析不出来就不要 —— 否则滚轮里会冒出一个「0 年」、
       月份块里冒出一个「0 月」，看着像 bug。 */
    if(y <= 0 || mon <= 0) return;
    if(!byDay.has(key)) byDay.set(key, { date:o.date, y, mon, items:[] });
    byDay.get(key).items.push({ dev:o.dev || '未知设备', time:o.time, amt });
  });
  ORDERS = [...byDay.entries()]
    .sort((a, b) => a[0] < b[0] ? 1 : -1)             /* 新的在上面 */
    .map(([, v]) => v);
  buildYears();
}

/* 从 ORDERS 派生「按年分组」。年份滚轮、当年小计、年度流水都吃它。 */
let YEARS = [];
function buildYears(){
  const map = new Map();
  ORDERS.forEach(g => {
    if(!map.has(g.y)) map.set(g.y, []);
    map.get(g.y).push(g);
  });
  YEARS = [...map.entries()]
    .sort((a, b) => b[0] - a[0])                      /* 新的在上面 */
    .map(([y, days]) => {
      const items = [];
      days.forEach(d => d.items.forEach(it => items.push(it)));
      return { y, days, items,
               sum:Math.round(items.reduce((a, b) => a + b.amt, 0) * 100) / 100 };
    });
}""", '订单分组函数')

# ═══════════════════════ ⑦ 登录：真发短信、真登录
rep("""$('bSend').addEventListener('click', () => {
  const b = $('bSend');
  if(b.disabled) return;
  b.disabled = true;
  let n = 60;""",
"""function startCountdown(){
  const b = $('bSend');
  b.disabled = true;
  let n = 60;""", '倒计时抽成函数')

rep("""  /* 演示：假装收到短信，把码填上，不然你没法往下走 */
  setTimeout(() => { $('iCode').value = '888888'; }, 900);
});""",
"""  codeT = setInterval(() => {
    n--;
    if(n <= 0){ clearInterval(codeT); codeT = null; b.disabled = false; b.textContent = '重新发送'; }
    else b.textContent = n + 's 后重发';
  }, 1000);
}

/* 这条是真发短信，别乱按 */
$('bSend').addEventListener('click', () => {
  const b = $('bSend');
  if(b.disabled) return;
  const phone = $('iPhone').value.trim();
  if(!/^1[0-9]{10}$/.test(phone)){ toast('手机号填完整'); return; }
  b.disabled = true; b.textContent = '发送中…';
  const r = JSON.parse(Android.sendCode(phone));
  if(r.ok){ startCountdown(); $('iCode').focus(); toast('验证码已发到 ' + phone); }
  else { b.disabled = false; b.textContent = '发送验证码'; toast(r.msg || '发不出去'); }
});""", '发送验证码改成真接口')

rep("""$('bLogin').addEventListener('click', () => {
  const b = $('bLogin');
  if(b.dataset.busy) return;
  b.dataset.busy = '1';
  b.disabled = true;
  $('plogo').classList.add('pop');
  setLabel(b, '登录中…');
  setTimeout(() => {
    showDevList();
    b.dataset.busy = '';
    b.disabled = false;
    setLabel(b, '登 录');
    $('plogo').classList.remove('pop');
  }, 950);
});""",
"""$('bLogin').addEventListener('click', () => {
  const b = $('bLogin');
  if(b.dataset.busy) return;
  const phone = $('iPhone').value.trim(), code = $('iCode').value.trim();
  if(!/^1[0-9]{10}$/.test(phone)){ toast('手机号填完整'); return; }
  if(code.length < 4){ toast('验证码填完整'); return; }

  b.dataset.busy = '1';
  b.disabled = true;
  $('plogo').classList.add('pop');
  setLabel(b, '登录中…');

  /* 请求是阻塞的，先让出 80ms 把"登录中…"画出来，否则界面直接卡住 */
  setTimeout(() => {
    const r = JSON.parse(Android.login(phone, code));
    b.dataset.busy = '';
    b.disabled = false;
    setLabel(b, '登 录');
    $('plogo').classList.remove('pop');
    if(r.ok) showDevList();
    else toast(r.msg || '登录失败');
  }, 80);
});""", '登录改成真接口')

# ═══════════════════════ ⑧ 进列表时把数据拉回来
rep("""function showDevList(){
  $('pLogin').classList.add('gone');
  setTimeout(() => { $('pLogin').style.display = 'none'; }, 320);
  setStep('dev');
  enterDev();
}""",
"""function showDevList(){
  loadDevices();
  loadOrders();
  $('pLogin').classList.add('gone');
  setTimeout(() => { $('pLogin').style.display = 'none'; }, 320);
  setStep('dev');
  enterDev();
}

function loadDevices(){
  DEV = JSON.parse(Android.devices()).map(d => ({ ...d, off: !d.online }));
  renderDevices();
}

function loadOrders(){
  setOrders(JSON.parse(Android.orders()));
  $('pOrder').classList.toggle('empty', ORDERS.length === 0);
  buildOrders();
  buildSummary();
}""", '进列表时拉数据')

# ═══════════════════════ ⑨ 扫码：相机在自己页面里，不走原生那个丑扫码器
rep("""$('bAdd').addEventListener('click', openScan);""",
"""/* 「+」→ 先要相机权限，拿到权限的结果从 window.onCamReady 回来 */
$('bAdd').addEventListener('click', () => Android.askCamera());

window.onCamReady = ok => {
  if(ok) openScan();
  else toast('没给相机权限，扫不了码');
};

let pendingDev = null;

/* ══ 取景框里的相机 ══ */
const cam = $('cam');
let camStream = null, camTimer = 0, camBusy = false;

const fc = document.createElement('canvas');
const fx = fc.getContext('2d', { willReadFrequently:true });

function scanHint(t){
  const el = document.querySelector('#pScan .shint');
  if(el) el.textContent = t;
}

function stopCam(){
  clearInterval(camTimer); camTimer = 0;
  camBusy = false;
  if(camStream){ camStream.getTracks().forEach(t => t.stop()); camStream = null; }
  if(cam) cam.srcObject = null;
}

function startCam(){
  stopCam();
  scanHint('把机器上的二维码放进取景框');
  if(!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia){
    scanHint('这台手机的网页调不起相机，用手动输入吧'); return;
  }
  navigator.mediaDevices.getUserMedia({
    audio:false,
    video:{ facingMode:{ ideal:'environment' }, width:{ ideal:1280 }, height:{ ideal:720 } }
  }).then(st => {
    camStream = st;
    cam.srcObject = st;
    cam.play().catch(() => {});
    /* 每 260ms 抓一帧。抓帧和解码都在别的线程，画面不会卡。 */
    camTimer = setInterval(grabFrame, 260);
  }).catch(e => {
    scanHint('相机打不开（' + ((e && e.name) || '未知') + '），用手动输入吧');
  });
}

/* 抓一帧 → 拉对比度 → 丢给原生那边的 ML Kit 解 */
function grabFrame(){
  if(camBusy || !cam.videoWidth) return;
  if($('pScan').classList.contains('done')) return;
  camBusy = true;
  try {
    const outW = 900, outH = Math.round(cam.videoHeight / cam.videoWidth * outW);
    fc.width = outW; fc.height = outH;
    fx.drawImage(cam, 0, 0, outW, outH);

    /* 机器上的码是蓝色背光屏：深蓝配浅蓝，对比度极低，手机自带扫码器就是这么挂的。
       把亮度的 6% 压到 0、94% 拉到 255，解码器立刻好认。 */
    const im = fx.getImageData(0, 0, outW, outH), d = im.data;
    const hist = new Uint32Array(256);
    for(let i = 0; i < d.length; i += 4){
      const g = (d[i] * 299 + d[i + 1] * 587 + d[i + 2] * 114) / 1000 | 0;
      d[i] = d[i + 1] = d[i + 2] = g;
      hist[g]++;
    }
    const total = d.length / 4;
    let acc = 0, lo = 0, hi = 255;
    for(let v = 0; v < 256; v++){ acc += hist[v]; if(acc >= total * 0.06){ lo = v; break; } }
    acc = 0;
    for(let v = 0; v < 256; v++){ acc += hist[v]; if(acc >= total * 0.94){ hi = v; break; } }
    const span = Math.max(hi - lo, 24), lut = new Uint8Array(256);
    for(let v = 0; v < 256; v++) lut[v] = Math.min(255, Math.max(0, ((v - lo) / span * 255) | 0));
    for(let i = 0; i < d.length; i += 4) d[i] = d[i+1] = d[i+2] = lut[d[i]];
    fx.putImageData(im, 0, 0);

    const hit = Android.scanFrame(fc.toDataURL('image/jpeg', 0.82));
    if(hit) gotCode(hit);
  } catch(e) { /* 这一帧不行就等下一帧 */ }
  camBusy = false;
}

function gotCode(raw){
  stopCam();
  $('pScan').classList.add('done');
  scanHint('扫到了，正在查这台机器…');
  const r = JSON.parse(Android.addDeviceFromCode(raw));
  if(!r.ok){
    $('pScan').classList.remove('done');
    scanHint((r.msg || '这台机器加不了') + ' —— 3 秒后重试');
    setTimeout(() => { if($('pScan').classList.contains('on')) startCam(); }, 3000);
    return;
  }
  pendingDev = r.device;
  $('sfName').textContent = pendingDev.name || '这台机器';
  $('sfSub').textContent  = deviceSub({ ...pendingDev, off: !pendingDev.online });
  scanHint('扫到了这台机器');
}

/* ══ 扫不出来时的手动兜底 ══ */
$('bManual').addEventListener('click', () => {
  stopCam();
  $('manual').classList.add('on');
  $('iSn').focus();
});

$('bSnNo').addEventListener('click', () => {
  $('manual').classList.remove('on');
  if(!$('pScan').classList.contains('done')) startCam();
});

$('bSnOk').addEventListener('click', () => {
  const no = $('iSn').value.trim();
  if(no.length < 6){ toast('设备号不止这么短'); return; }
  const r = JSON.parse(Android.addDeviceByNo(no));
  if(!r.ok){ toast(r.msg || '这个号查不到机器'); return; }
  $('manual').classList.remove('on');
  pendingDev = r.device;
  $('sfName').textContent = pendingDev.name || '这台机器';
  $('sfSub').textContent  = deviceSub({ ...pendingDev, off: !pendingDev.online });
  $('pScan').classList.add('done');
  scanHint('查到了这台机器');
});""", '「+」接页面内相机')

rep('let scanT = null;\n', '', '去掉扫码定时器变量')

rep("""function openScan(){
  setStep('scan');
  $('pScan').classList.remove('done');
  $('pScan').classList.add('entering');
  clearTimeout(scanT);""",
"""function openScan(){
  setStep('scan');
  $('pScan').classList.remove('done');
  $('manual').classList.remove('on');
  $('pScan').classList.add('entering');""", 'openScan 回到未扫到状态')

rep("""    requestAnimationFrame(() => requestAnimationFrame(() => {
      $('pScan').classList.remove('entering');
    }));
    scanT = setTimeout(() => $('pScan').classList.add('done'), 1500);
  }, null, 'var(--bg)', 17);""",
"""    requestAnimationFrame(() => requestAnimationFrame(() => {
      $('pScan').classList.remove('entering');
    }));
    startCam();          /* 页面长出来了才开相机，不然第一帧全是黑的 */
  }, null, 'var(--bg)', 17);""", 'openScan 里开相机')

rep("""function closeScan(){
  clearTimeout(scanT);
  collapseTo(() => $('pScan').classList.remove('on'));
}""",
"""function closeScan(){
  stopCam();
  $('manual').classList.remove('on');
  collapseTo(() => $('pScan').classList.remove('on'));
}""", '关扫码页时关相机')

# ═══════════════════════ ⑩ 添加设备
rep("""$('bAddDev').addEventListener('click', () => {
  const id = 2, name = '1号楼南热水';
  if(!document.querySelector('.dcard[data-d="' + id + '"]')){
    const c = document.createElement('div');
    c.className = 'dcard';
    c.dataset.d = id;
    c.innerHTML = '<span class="dot"></span>' +
      '<div class="i"><div class="n">' + name + '</div>' +
      '<div class="p">0.011 元/100mL · 空闲</div></div>' +
      '<span class="arw"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg></span>';
    c.addEventListener('click', () => openWater(id));
    DEV.push({ name: name, off: false });
    $('dlist').appendChild(c);
  }
  closeScan();
  setTimeout(() => {
    const c = document.querySelector('.dcard[data-d="2"]');
    c.classList.remove('in'); void c.offsetWidth; c.classList.add('in');
    toast('已添加 · ' + name);
  }, 340);
  setStep('dev');
});""",
"""$('bAddDev').addEventListener('click', () => {
  const name = (pendingDev && pendingDev.name) || '';
  loadDevices();
  closeScan();
  setTimeout(() => {
    /* 新设备永远插在最前面，所以就是第 0 张卡 */
    const c = document.querySelector('.dcard[data-d="0"]');
    if(c){ c.classList.remove('in'); void c.offsetWidth; c.classList.add('in'); }
    toast('已添加 · ' + name);
    pendingDev = null;
  }, 340);
  setStep('dev');
});""", '添加设备接真数据')

# ═══════════════════════ ⑪ 解锁：开机器（只解锁，不出水）
rep("""  busy = true;
  bUnlock.disabled = true;
  setLabel(bUnlock, '正在解锁…');

  /* 0ms —— 睁眼 + 锁抖三下 */
  wrap.classList.add('waking');""",
"""  busy = true;
  bUnlock.disabled = true;
  setLabel(bUnlock, '正在解锁…');

  /* ⚠️ 解锁只开机器、**不出水**（出水靠人在机器上按按键）；
     但这个口子一开，之后谁按都记在本账号上。
     请求是阻塞的，先让出 80ms 把"正在解锁…"画出来，否则界面直接卡住。 */
  setTimeout(() => {
    const r = JSON.parse(Android.unlock(DEV[curDev].skuId));
    if(!r.ok){
      busy = false;
      bUnlock.disabled = false;
      setLabel(bUnlock, '解锁设备');
      toast(r.msg || '解锁失败');
      return;
    }
    runUnlockAnim();
  }, 80);
});

function runUnlockAnim(){
  /* 0ms —— 睁眼 + 锁抖三下 */
  wrap.classList.add('waking');""", '解锁接真接口')

rep("""    bEnd.disabled = false;
  });
});

bEnd.addEventListener('click', () => {""",
"""    bEnd.disabled = false;
  });
}

bEnd.addEventListener('click', () => {""", '收尾解锁动画函数')

# ═══════════════════════ ⑫ 启动
rep("""const HASH = location.hash;
if(HASH === '#auto'){
  history.replaceState(null, '', '#');
  setStep('login');
  setTimeout(autoPlay, 700);
} else if(HASH === '#dev'){
  /* 跳过登录 —— 给旧的回归测试用 */
  $('pLogin').style.display = 'none';
  setStep('dev');
  enterDev();
} else {
  setStep('login');
}

buildOrders();
buildSummary();""",
"""setStep('login');
/* 已经登录过就直接进列表，不用每次重登一遍 */
if(Android.hasToken()) showDevList();

/* 安卓返回键先问网页：子页面开着就先收起子页面 */
window.onBack = () => {
  if($('manual').classList.contains('on')){ $('bSnNo').click(); return 'handled'; }
  if($('pSummary').classList.contains('on')){ $('bkS').click(); return 'handled'; }
  if($('pScan').classList.contains('on')){ closeScan(); return 'handled'; }
  if($('pWater').classList.contains('on')){ $('bk').click(); return 'handled'; }
  return 'exit';
};""", '启动逻辑')

# ═══════════════════════ ⑬ 登录页的几处修正 + 出错必可见
# ① 验证码是 4 位，不是 6 位；提示语也不要 —— 上面已经写着"手机号""验证码"了
rep('<input id="iPhone" inputmode="numeric" placeholder="11 位手机号">',
    '<input id="iPhone" inputmode="numeric">', '去掉手机号的占位提示')
rep('<input id="iCode" inputmode="numeric" placeholder="6 位数字">',
    '<input id="iCode" inputmode="numeric">', '去掉验证码的占位提示')

# ② 桥调用一律包一层。原来 Android.xxx() 万一抛异常，JSON.parse 炸掉，
#    后面的"恢复按钮"就永远不执行 —— 按钮灰在那里像个死按钮，也不报错。
rep("""/* ══════════════ 登录 ══════════════ */""",
"""/* ══════════════ 登录 ══════════════ */
/* 桥调用统一走这里：原生那边万一抛异常，JS 会拿到 undefined，
   JSON.parse 就会炸，后面的"把按钮恢复回来"就永远执行不到 —— 界面直接卡死。
   所以每一步桥调用都必须包住，出错也要当成一次正常的失败返回。 */
function fromNative(fn, what){
  try {
    const raw = fn();
    if(typeof raw !== 'string' || !raw) throw new Error('原生没有返回内容');
    return JSON.parse(raw);
  } catch(e){
    return { ok:false, msg:(what || '操作') + '出错：' + ((e && e.message) || e) };
  }
}

/* 任何漏网的报错都弹出来，不许静默失败 */
window.onerror = (m) => { toast('出错了：' + m); return false; };""",
    '桥调用包一层 + 全局错误可见')

# ③ 发验证码：4 位
rep("""  const r = JSON.parse(Android.sendCode(phone));
  if(r.ok){ startCountdown(); $('iCode').focus(); toast('验证码已发到 ' + phone); }""",
"""  const r = fromNative(() => Android.sendCode(phone), '发送验证码');
  if(r.ok){ startCountdown(); $('iCode').focus(); toast('验证码已发到 ' + phone); }""",
    '发验证码走安全包装')

# ④ 登录：4 位 + 无论如何都把按钮恢复
rep("""  if(!/^1[0-9]{10}$/.test(phone)){ toast('手机号填完整'); return; }
  if(code.length < 4){ toast('验证码填完整'); return; }

  b.dataset.busy = '1';
  b.disabled = true;
  $('plogo').classList.add('pop');
  setLabel(b, '登录中…');

  /* 请求是阻塞的，先让出 80ms 把"登录中…"画出来，否则界面直接卡住 */
  setTimeout(() => {
    const r = JSON.parse(Android.login(phone, code));
    b.dataset.busy = '';
    b.disabled = false;
    setLabel(b, '登 录');
    $('plogo').classList.remove('pop');
    if(r.ok) showDevList();
    else toast(r.msg || '登录失败');
  }, 80);
});""",
"""  if(!/^1[0-9]{10}$/.test(phone)){ toast('手机号填完整'); return; }
  if(code.length < 4){ toast('验证码填完整'); return; }

  b.dataset.busy = '1';
  b.disabled = true;
  $('plogo').classList.add('pop');
  setLabel(b, '登录中…');

  /* 无论如何都要把按钮放回原样 —— 错了也一样，不然它就一直灰在那里 */
  const restore = () => {
    b.dataset.busy = '';
    b.disabled = false;
    setLabel(b, '登 录');
    $('plogo').classList.remove('pop');
  };

  /* 请求是阻塞的，先让出 80ms 把"登录中…"画出来，否则界面直接卡住 */
  setTimeout(() => {
    const r = fromNative(() => Android.login(phone, code), '登录');
    if(r.ok){ restore(); showDevList(); }
    else { restore(); toast(r.msg || '登录失败'); }
  }, 80);
});""", '登录：4 位 + 按钮必定恢复')

# ⑤ 解锁那条也一样，别卡死在"正在解锁…"
rep("""    const r = JSON.parse(Android.unlock(DEV[curDev].skuId));""",
"""    const r = fromNative(() => Android.unlock(DEV[curDev].skuId), '解锁');""",
    '解锁走安全包装')

rep("""  const r = JSON.parse(Android.addDeviceFromCode(raw));""",
"""  const r = fromNative(() => Android.addDeviceFromCode(raw), '识别');""",
    '扫码识别走安全包装')

rep("""  const r = JSON.parse(Android.addDeviceByNo(no));""",
"""  const r = fromNative(() => Android.addDeviceByNo(no), '查询');""",
    '手动输号走安全包装')

rep("""  DEV = JSON.parse(Android.devices()).map(d => ({ ...d, off: !d.online }));""",
"""  DEV = (JSON.parse(Android.devices()) || []).map(d => ({ ...d, off: !d.online }));""",
    '设备列表容错')

rep("""  setOrders(JSON.parse(Android.orders()));""",
"""  setOrders(JSON.parse(Android.orders()) || []);""",
    '订单列表容错')

# ═══════════════════════ ⑭ 水滴变成一张脸
# yui540 那套的核心：形状要会挤、动作要有回弹、间隔带随机（不然像上发条）。
# 这里水滴放大到 128×168，中间挖出两只眼睛和一张嘴（用页面底色当"挖空"色），
# 输入手机号时向下看，输入验证码时向上看，验证码错了做鬼脸，登录成功眯眼笑。
rep("""        <svg class="plogo" id="plogo" viewBox="0 0 76 100">
          <path class="tip" d="M 38 6 C 46 32 68 44 68 62 C 68 82 54 94 38 94 C 22 94 8 82 8 62 C 8 44 30 32 38 6 Z"/>
        </svg>""",
"""        <div class="plwrap">
          <svg class="plogo" id="plogo" viewBox="0 0 76 100">
            <path class="tip" d="M 38 6 C 46 32 68 44 68 62 C 68 82 54 94 38 94 C 22 94 8 82 8 62 C 8 44 30 32 38 6 Z"/>
            <g class="eyes">
              <ellipse class="edot" cx="28" cy="67" rx="3.7" ry="4.7"/>
              <ellipse class="edot" cx="48" cy="67" rx="3.7" ry="4.7"/>
              <path class="ejoy" d="M23.5 69.5 q4.5 -6.5 9 0"/>
              <path class="ejoy" d="M43.5 69.5 q4.5 -6.5 9 0"/>
              <path class="esad" d="M23.5 63.5 l9 8"/>
              <path class="esad" d="M32.5 63.5 l-9 8"/>
              <path class="esad" d="M43.5 63.5 l9 8"/>
              <path class="esad" d="M52.5 63.5 l-9 8"/>
            </g>
            <path class="mouth" d="M32 80 q6 5 12 0"/>
          </svg>
        </div>""", '水滴换成一张脸')

FACE_CSS = u'''
  /* ═══ 水滴脸 ═══
     眼睛和嘴用页面底色画，看起来就是从水滴上挖空的。
     看的方向用 --gx/--gy 两个变量推，别的地方想推它只要改变量就行。 */
  .plwrap{ margin:0 auto 18px; display:block; position:relative;
           animation:breathe 3.8s ease-in-out infinite }
  @keyframes breathe{ 0%,100%{ transform:scale(1) } 50%{ transform:scale(1.03,1.045) } }
  .plogo{ --gx:0px; --gy:0px; --gs:1; --heat:0;
          width:128px; height:168px; display:block; overflow:visible;
          cursor:pointer; touch-action:manipulation }
  /* 看的方向：位移 + 缩放。
     只靠 translate 不够 —— 眼睛在 128px 宽的水滴里挪 3px，
     看起来和待机没区别。往上看时同时缩一点（像在矁远处），
     方向感才出来。 */
  .plogo .eyes{ transform:translate(var(--gx), var(--gy)) scale(var(--gs,1));
                transition:transform .38s var(--spring) }
  .plogo .edot{ fill:var(--bg); transition:opacity .1s linear }
  .plogo .ejoy, .plogo .esad{ fill:none; stroke:var(--bg); stroke-width:2.7;
                              stroke-linecap:round; opacity:0;
                              transition:opacity .12s linear }
  .plogo .mouth{ fill:none; stroke:var(--bg); stroke-width:2.7; stroke-linecap:round;
                 transition:d .34s var(--spring) }
  .plogo .edot{ transform-box:fill-box; transform-origin:center;
                transition:transform .12s ease-out, opacity .1s linear }

  /* 往上看：移得比往下多，而且缩小 —— 眼睛小一圈更像在矁高处。
     原来只有 -3.2px，在 128px 宽的水滴里根本看不出来。 */
  .plogo.look-up{ --gy:-9px; --gs:.88 }
  /* 往下看：保持小幅度。眼睛 cy=67、嘴在 y=80，
     往下挪多了眼睛右缘就贴到嘴的起点（x=32）上，看着像哭。 */
  .plogo.look-down{ --gy:3.6px }
  .plogo.blink .edot{ transform:scaleY(.06) }

  .plogo.ok .edot, .plogo.bad .edot{ opacity:0 }
  .plogo.ok .ejoy{ opacity:1 }
  .plogo.bad .esad{ opacity:1 }
  .plogo.ok .mouth{ d:path("M30 79 q8 11 16 0") }
  .plogo.bad .mouth{ d:path("M32 84.5 q6 -5.5 12 0") }

  /* 挤一下再弹回来 —— yui540 那种手感全靠这一步 */
  .plogo.ok{ animation:faceJoy .72s var(--spring) }
  @keyframes faceJoy{
    0%{ transform:scale(1,1) } 30%{ transform:scale(1.18,.84) }
    58%{ transform:scale(.92,1.1) } 80%{ transform:scale(1.04,.98) }
    100%{ transform:scale(1,1) }
  }
  .plogo.bad{ animation:faceNo .54s var(--spring) }
  @keyframes faceNo{
    0%,100%{ transform:translateX(0) rotate(0) }
    18%{ transform:translateX(-6px) rotate(-4deg) }
    42%{ transform:translateX(5px) rotate(3deg) }
    66%{ transform:translateX(-3px) rotate(-1.6deg) }
  }

  /* ═══ 彩蛋：渐渐变红 ═══
     --heat 0→1 由 JS 推（第 12 下起），JS 同时算出插值后的颜色直接写到
     tip 的 inline style 上。

     为什么不用 color-mix：WebView 里 CSS.supports('fill','color-mix(...)') 回 true，
     但 getComputedStyle 拿到的是 rgb(0,0,0) —— SVG 的 fill 不吃 color-mix，
     它只会在声明层说“我支持”，渲染时直接摆烂。
     写 inline fill 还有个好处：transition:fill 能真的跑起来。 */
  .plogo .tip{
    transform-box:fill-box; transform-origin:bottom;
    transition:fill .5s linear;
  }

  /* 被戳：挤压一下 */
  .plogo.poke .tip{ animation:poke .42s var(--pop) }
  @keyframes poke{
    0%  { transform:scale(1,1) }
    28% { transform:scale(1.16,.84) }
    55% { transform:scale(.93,1.1) }
    78% { transform:scale(1.04,.97) }
    100%{ transform:scale(1,1) }
  }
  /* 生气：整个抖 */
  .plogo.mad{ animation:madShake .4s ease-out }
  @keyframes madShake{
    0%,100%{ transform:translateX(0) }
    15%{ transform:translateX(-5px) rotate(-3deg) }
    35%{ transform:translateX(5px) rotate(3deg) }
    55%{ transform:translateX(-4px) rotate(-2deg) }
    75%{ transform:translateX(3px) rotate(1.5deg) }
  }
  /* 炸之前：鼓起来 */
  .plogo.swell{ animation:swell .34s cubic-bezier(.3,1.4,.5,1) forwards }
  @keyframes swell{
    0%  { transform:scale(1) }
    60% { transform:scale(1.22) }
    100%{ transform:scale(1.3) }
  }

  /* ═══ 彩蛋：气泡 ═══ */
  .bub{
    position:absolute; left:50%; bottom:calc(100% + 14px);
    transform:translateX(-50%) scale(.4) translateY(14px);
    transform-origin:bottom center;
    background:var(--dark); color:#FFF3EF;
    font-size:13.5px; font-weight:650; line-height:1.42; letter-spacing:.2px;
    padding:10px 15px; border-radius:15px; white-space:nowrap;
    opacity:0; pointer-events:none;
    box-shadow:0 8px 24px rgba(43,28,24,.28);
    transition:transform .3s var(--spring), opacity .16s linear;
    z-index:5;
  }
  .bub::after{
    content:""; position:absolute; top:100%; left:50%; margin-left:-7px;
    border:7px solid transparent; border-top-color:var(--dark); border-bottom:0;
  }
  .bub.on{ opacity:1; transform:translateX(-50%) scale(1) translateY(0) }
  .bub.off{ opacity:0; transform:translateX(-50%) scale(.92) translateY(-4px);
            transition:transform .13s ease-in, opacity .13s linear }
  .bub.wide{ white-space:normal; width:210px; text-align:center; font-weight:600 }

  /* ═══ 彩蛋：炸开 ═══
     从水滴中心炸出去铺满全屏。clip-path 圆形扩散比 scale 好 ——
     不会把底下的内容一起拉伸变形。
     半径要够大：circle() 的百分比是相对「参考框对角线/√2」的，
     圆心在 41% 高度处，到最远的角差不多要 120%，留余量给 170%。 */
  .burst{
    position:absolute; inset:0; z-index:30; pointer-events:none;
    background:var(--danger);
    clip-path:circle(0px at 50% 41%);
    transition:clip-path .68s cubic-bezier(.28,.86,.36,1);
  }
  .burst.on{ clip-path:circle(170% at 50% 41%) }
  /* 炸开瞬间的一圈白光 */
  .flash{
    position:absolute; inset:0; z-index:31; pointer-events:none;
    background:radial-gradient(circle at 50% 41%, #FFF 0%, rgba(255,255,255,0) 46%);
    opacity:0;
  }
  .flash.on{ animation:flash .5s ease-out }
  @keyframes flash{ 0%{ opacity:1 } 100%{ opacity:0 } }
'''
rep("""        <div class="plwrap">""",
"""        <div class="plwrap">
          <div class="bub" id="bub"></div>""", '气泡的 HTML')

# 炸开层要挂在 .scr 最外层，不能放 #pLogin 里 ——
# enterDemo() 会把登录页 display:none，炸开层跟着一起消失，
# 表现就是“炸到一半突然没了”。跟 .toast 是同一个道理。
rep("""    <div class="toast" id="toast"></div>""",
"""    <div class="toast" id="toast"></div>
    <div class="burst" id="burst"></div>
    <div class="flash" id="flash"></div>""", '炸开层挂到 .scr 外层')

rep('</style>', FACE_CSS + '</style>', '水滴脸 CSS')

rep("""/* ══════════════ 登录 ══════════════ */""",
"""/* ══════════════ 登录 ══════════════ */
/* ══ 水滴会看人 ══ */
const face = $('plogo');

function faceMood(m){
  face.classList.remove('look-down','look-up','ok','bad');
  if(m) face.classList.add(m);
}

$('iPhone').addEventListener('focus', () => faceMood('look-down'));
$('iPhone').addEventListener('blur',  () => faceMood(''));
$('iCode').addEventListener('focus',  () => faceMood('look-up'));
$('iCode').addEventListener('blur',   () => faceMood(''));

function faceBusy(){
  return face.classList.contains('ok') || face.classList.contains('bad') ||
         document.activeElement === $('iPhone') || document.activeElement === $('iCode');
}

/* 眨眼。间隔带随机 —— 固定节拍一眼就看出是机器。
   用 setTimeout 递归，不用 setInterval：setInterval 的间隔只在创建时算一次，
   之后永远是那个数，写着 Math.random() 也是假的。 */
(function blinkLoop(){
  setTimeout(() => {
    if(!faceBusy()){
      face.classList.add('blink');
      setTimeout(() => face.classList.remove('blink'), 115);
    }
    blinkLoop();
  }, 3100 + Math.random() * 1400);
})();

/* 闲着的时候偶尔瞟一眼旁边 */
(function glanceLoop(){
  setTimeout(() => {
    if(!faceBusy()){
      const dx = [-2.8, 2.8, 0, 1.9, -1.9][Math.floor(Math.random() * 5)];
      face.style.setProperty('--gx', dx + 'px');
      setTimeout(() => face.style.setProperty('--gx', '0px'), 850 + Math.random() * 900);
    }
    glanceLoop();
  }, 3000 + Math.random() * 2600);
})();

/* ══ 气泡 ══
   登录页的提示都从这里出，不再用底部弹上来的 toast ——
   提醒就该从水滴嘴里说出来，而不是从屏幕底下冒出来。 */
const bub = $('bub');
let bubT = null;
function say(text, wide){
  bub.textContent = text;
  bub.classList.toggle('wide', !!wide);
  bub.classList.remove('on','off');
  void bub.offsetWidth;
  bub.classList.add('on');
  clearTimeout(bubT);
  bubT = setTimeout(() => {
    bub.classList.remove('on');
    bub.classList.add('off');
  }, wide ? 2200 : Math.max(1400, text.length * 145));
}
function hush(){
  clearTimeout(bubT);
  bub.classList.remove('on');
  bub.classList.add('off');
}
/* 说一句 + 配个表情 + 震一下，登录页的错误提示都是这个形状 */
function quip(msg, mood){
  say(msg, true);
  faceMood(mood || 'bad');
  buzz(30);
  setTimeout(() => faceMood(''), 1300);
}

/* ══ 彩蛋：点水滴 ══
   15 下。只有第 3/8/12/15 下说话 —— 中间那些下数纯靠形变和振动
   给反馈。每次都弹话就不叫彩蛋了，那叫客服。 */
const LINES = {
  3:  { t:'痒。',          m:'',    v:16 },
  8:  { t:'手不想要了？',  m:'bad', v:30 },
  12: { t:'……我记住你了', m:'bad', v:42 },
  15: { t:'好吧，你赢了',  m:'ok',  v:0  }
};
const TOTAL = 15;
let pokes = 0, pokeT = null, coolT = null;

function buzz(ms){
  try{ Android.buzz(ms || 0); }catch(e){}
}

/* 第 12 下起渐渐变红。不是一下跳过去 —— 每次只推一点，
   而且越往后推得越多，看起来像被点着了。

   颜色在这里算，不用 CSS 的 color-mix —— WebView 里 SVG 的 fill 不吃它，
   CSS.supports 会说支持，实际渲染成黑色。
   直接写 inline fill，transition:fill 才会真的跑。 */
const TIP = document.querySelector('.plogo .tip');
const C_FROM = [0x8F, 0x4C, 0x38];   /* --primary 锈红 */
const C_TO   = [0xF0, 0x34, 0x2F];   /* --danger  亮红 */
function heatTo(p){
  const heat = p < 12 ? 0 : Math.min(1, (p - 11) / 4);   /* 12→.25 13→.5 14→.75 15→1 */
  const c = C_FROM.map((v, i) => Math.round(v + (C_TO[i] - v) * heat));
  if(TIP) TIP.style.fill = 'rgb(' + c.join(',') + ')';
  face.style.setProperty('--heat', heat.toFixed(3));
}

face.addEventListener('click', () => {
  if(demoMode) return;
  if(pokes >= TOTAL) return;

  pokes++;
  const line = LINES[pokes];
  const last = pokes >= TOTAL;

  /* 停手一会儿就消气 —— 不然点到第 12 下变红了就走开，
     水滴会一直红着，下次回来像个 bug。 */
  clearTimeout(coolT);
  if(!last) coolT = setTimeout(() => {
    if(demoMode) return;
    pokes = 0;
    heatTo(0);
    faceMood('');
    face.classList.remove('mad','poke');
  }, 3200);

  if(pokes >= 8 && !last) faceMood('bad');

  face.classList.remove('poke','mad','swell');
  void face.offsetWidth;
  face.classList.add(pokes >= 8 ? 'mad' : 'poke');

  heatTo(pokes);
  if(line) say(line.t);
  if(!last) buzz(line ? line.v : Math.round(10 + pokes * 1.6));

  clearTimeout(pokeT);

  if(last){
    /* 第 15 下：鼓起来 → 从中间炸开 → 铺满 → 直接进演示模式 */
    faceMood('ok');
    pokeT = setTimeout(() => {
      face.classList.remove('mad','poke');
      face.classList.add('swell');
      buzz(60);
      setTimeout(() => {
        $('burst').classList.add('on');
        $('flash').classList.add('on');
        setTimeout(() => buzz(90), 120);
        setTimeout(() => {
          enterDemo();
          setTimeout(() => {
            $('burst').classList.remove('on');
            $('flash').classList.remove('on');
            face.classList.remove('swell');
            face.style.setProperty('--heat', '0');
            if(TIP) TIP.style.fill = '';
            hush();
            pokes = 0;
          }, 380);
        }, 560);
      }, 300);
    }, 620);
  } else {
    pokeT = setTimeout(() => {
      if(pokes < 8) faceMood('');
      face.classList.remove('mad','poke');
    }, 900);
  }
});""", '水滴表情逻辑')

rep("""    const r = fromNative(() => Android.login(phone, code), '登录');
    if(r.ok){ restore(); showDevList(); }
    else { restore(); toast(r.msg || '登录失败'); }""",
"""    const r = fromNative(() => Android.login(phone, code), '登录');
    restore();
    if(r.ok){
      faceMood('ok');                      /* 眯眼笑一下再进列表 */
      showDevList();
    } else {
      quip(r.msg || '登录失败');           /* 做个鬼脸 + 气泡说话 */
    }""", '登录结果驱动表情')

rep("""  const r = fromNative(() => Android.sendCode(phone), '发送验证码');
  if(r.ok){ startCountdown(); $('iCode').focus(); toast('验证码已发到 ' + phone); }
  else { b.disabled = false; b.textContent = '发送验证码'; toast(r.msg || '发不出去'); }""",
"""  const r = fromNative(() => Android.sendCode(phone), '发送验证码');
  if(r.ok){
    startCountdown(); $('iCode').focus();
    say('验证码已发到 ' + phone);
  } else {
    b.disabled = false; b.textContent = '发送验证码';
    quip(r.msg || '发不出去');
  }""", '发送失败也做鬼脸')

# 登录页一进来就摆个"看手机"的姿态
rep("""setStep('login');
/* 已经登录过就直接进列表，不用每次重登一遍 */""",
"""setStep('login');
/* 已经登录过就直接进列表，不用每次重登一遍 */""", '启动逻辑保持')

# ═══════════════════════ ⑮ 把"我现在登的是谁"明明白白摆在界面上
# 起因：注册接口 /user/reg 对不存在的手机号 + 任意验证码会当场新建一个空账号，
# 于是随手输一个号也能"登进去"，且完全看不出登的是谁。这种事不许再无声发生。
rep('''      <div class="appbar"><h2 style="margin-left:2px">设备</h2><span class="sp"></span>''',
'''      <div class="appbar">
        <button class="acct" id="bAcct" type="button" aria-expanded="false">
          <b>设备</b>
          <span class="who"><i id="acct">未登录</i><svg class="chev" viewBox="0 0 24 24"
            fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"
            stroke-linejoin="round"><path d="M6 9l6 6 6-6"/></svg></span>
        </button>
        <span class="sp"></span>''', '设备页标题下挂当前账号 + 下拉箭头（原来只是一行灰字，看不出能点）')

rep('''      <div class="dlist" id="dlist"></div>
      <div class="tabbar">''',
'''      <div class="dlist" id="dlist"></div>

      <div class="manual" id="acctBox">
        <div class="mbox">
          <div class="mt">当前账号</div>
          <div class="ms" id="acctNo">—</div>
          <button class="btn fill" id="bLogout" type="button"><span class="lb">退出登录</span></button>
          <button class="btn dark" id="bAcctNo" type="button"><span class="lb">算了</span></button>
        </div>
      </div>

      <div class="tabbar">''', '账号浮层')

ACCT_CSS = u'''
  /* ═══ 当前账号 ═══
     点标题就能看到登的是哪个手机号，也能退出。
     起因是 /user/reg 对任意手机号都会静默新建账号，界面必须能看出登的是谁。

     那枚小箭头和淡色胶囊是专门补的：原来只是一行灰字，没人看得出那是个按钮。 */
  .acct{ border:0; background:none; font:inherit; text-align:left; cursor:pointer;
         padding:0; margin-left:2px; display:flex; flex-direction:column; gap:2px }
  .acct b{ font-size:19px; font-weight:700; letter-spacing:-.02em; color:var(--on-surface) }
  .acct .who{ display:inline-flex; align-items:center; gap:3px;
              padding:2px 7px 2px 8px; border-radius:999px;
              background:rgba(143,76,56,.09);
              transition:background .18s ease }
  .acct:active .who{ background:rgba(143,76,56,.19) }
  .acct .who i{ font-style:normal; font-size:10.5px; font-weight:600;
                color:var(--primary); letter-spacing:.03em }
  .acct .chev{ width:11px; height:11px; flex:none; color:var(--primary);
               transition:transform .28s var(--spring) }
  .acct[aria-expanded="true"] .chev{ transform:rotate(180deg) }
'''
rep('</style>', ACCT_CSS + '</style>', '账号样式')

rep("""window.onCamReady = ok => {""",
"""function setAcct(){
  const p = (Android.phone && Android.phone()) || '';
  $('acct').textContent = p || '未登录';
  $('acctNo').textContent = p || '没登录';
}

$('bAcct').addEventListener('click', () => {
  setAcct();
  $('acctBox').classList.add('on');
  $('bAcct').setAttribute('aria-expanded', 'true');
});
$('bAcctNo').addEventListener('click', () => {
  $('acctBox').classList.remove('on');
  $('bAcct').setAttribute('aria-expanded', 'false');
});
$('bLogout').addEventListener('click', () => {
  Android.logout();
  location.reload();
});

window.onCamReady = ok => {""", '账号逻辑')

# 登录成功时报出手机号 —— 登错号立刻看得见
rep("""    if(r.ok){
      faceMood('ok');                      /* 眯眼笑一下再进列表 */
      showDevList();
    } else {""",
"""    if(r.ok){
      faceMood('ok');                      /* 眯眼笑一下再进列表 */
      const who = (Android.phone && Android.phone()) || phone;
      toast('已登录 · ' + who);            /* 登错号要当场看得出来 */
      setTimeout(showDevList, 420);
    } else {""", '登录成功报出手机号')

# 进列表和启动时都要刷新账号
rep("""function showDevList(){
  loadDevices();""",
"""function showDevList(){
  setAcct();
  loadDevices();""", '进列表刷新账号')

rep("""  if($('manual').classList.contains('on')){ $('bSnNo').click(); return 'handled'; }""",
"""  if($('acctBox').classList.contains('on')){ $('bAcctNo').click(); return 'handled'; }
  if($('manual').classList.contains('on')){ $('bSnNo').click(); return 'handled'; }""",
    '返回键先收账号浮层')

# ═══════════════════════ ⑯ 桥调用改成异步：页面不再被网络请求冻住
# 起因：@JavascriptInterface 的方法虽然跑在原生后台线程，但**网页那边是同步等它返回的**
# —— 一个 3 秒的网络请求，就是把整个页面冻 3 秒（点不动、动画停，看着像死机）。
# 所以原生侧那几处会联网的方法全改成了「立刻返回 → 后台线程干活 → 调 window.onXxx(json)」，
# 这里把对应的调用点改成回调式。回调拿到的还是 json 字符串，照旧走 fromNative 解析。
# 原生侧的回调名（对应 AndroidBridge.kt 里 done(...) 的第一个参数）：
#   onHasToken  onSendCode  onLogin  onOrders  onAddDevice  onAddDeviceNo  onUnlock

rep("""setStep('login');
/* 已经登录过就直接进列表，不用每次重登一遍 */
if(Android.hasToken()) showDevList();""",
"""setStep('login');
/* 已经登录过就直接进列表，不用每次重登一遍。
   结果从 window.onHasToken 回来 —— 原生在后台线程验，页面不冻。 */
window.onHasToken = v => { if(v === '1') showDevList(); };
Android.hasToken();""", '启动：hasToken 改异步')

rep("""  b.disabled = true; b.textContent = '发送中…';
  const r = fromNative(() => Android.sendCode(phone), '发送验证码');
  if(r.ok){
    startCountdown(); $('iCode').focus();
    say('验证码已发到 ' + phone);
  } else {
    b.disabled = false; b.textContent = '发送验证码';
    quip(r.msg || '发不出去');
  }
});""",
"""  b.disabled = true; b.textContent = '发送中…';
  /* 发完了原生会喊 window.onSendCode，这里不等它 */
  Android.sendCode(phone);
});

window.onSendCode = raw => {
  const r = fromNative(() => raw, '发送验证码');
  if(r.ok){
    startCountdown(); $('iCode').focus();
    say('验证码已发到 ' + $('iPhone').value.trim());
  } else {
    const b = $('bSend');
    b.disabled = false; b.textContent = '发送验证码';
    quip(r.msg || '发不出去');
  }
};""", '发验证码改异步')

rep("""  /* 无论如何都要把按钮放回原样 —— 错了也一样，不然它就一直灰在那里 */
  const restore = () => {
    b.dataset.busy = '';
    b.disabled = false;
    setLabel(b, '登 录');
    $('plogo').classList.remove('pop');
  };

  /* 请求是阻塞的，先让出 80ms 把"登录中…"画出来，否则界面直接卡住 */
  setTimeout(() => {
    const r = fromNative(() => Android.login(phone, code), '登录');
    restore();
    if(r.ok){
      faceMood('ok');                      /* 眯眼笑一下再进列表 */
      const who = (Android.phone && Android.phone()) || phone;
      toast('已登录 · ' + who);            /* 登错号要当场看得出来 */
      setTimeout(showDevList, 420);
    } else {
      quip(r.msg || '登录失败');           /* 做个鬼脸 + 气泡说话 */
    }
  }, 80);
});""",
"""  /* 登录结果从 window.onLogin 回来，这里不等它 */
  Android.login(phone, code);
});

window.onLogin = raw => {
  const b = $('bLogin');
  const r = fromNative(() => raw, '登录');

  /* 无论如何都要把按钮放回原样 —— 错了也一样，不然它就一直灰在那里 */
  b.dataset.busy = '';
  b.disabled = false;
  setLabel(b, '登 录');
  $('plogo').classList.remove('pop');

  if(r.ok){
    faceMood('ok');                      /* 眯眼笑一下再进列表 */
    const who = (Android.phone && Android.phone()) || $('iPhone').value.trim();
    say('已登录 · ' + who);              /* 登错号要当场看得出来 */
    setTimeout(showDevList, 420);
  } else {
    quip(r.msg || '登录失败');           /* 做个鬼脸 + 气泡说话 */
  }
};""", '登录改异步')

rep("""function loadOrders(){
  setOrders(JSON.parse(Android.orders()) || []);
  $('pOrder').classList.toggle('empty', ORDERS.length === 0);
  buildOrders();
  buildSummary();
}""",
"""function loadOrders(force){
  /* 演示模式下不去问真实订单 —— 和 loadDevices 同一个道理。 */
  if(demoMode) return;
  Android.orders(!!force);   /* 结果从 window.onOrders 回来 */
}

window.onOrders = raw => {
  if(demoMode) return;       /* 双保险 */
  setOrders(JSON.parse(raw) || []);
  $('pOrder').classList.toggle('empty', ORDERS.length === 0);
  buildOrders();
  buildSummary();
};""", '订单改异步')

rep("""  const r = fromNative(() => Android.addDeviceFromCode(raw), '识别');
  if(!r.ok){
    $('pScan').classList.remove('done');
    scanHint((r.msg || '这台机器加不了') + ' —— 3 秒后重试');
    setTimeout(() => { if($('pScan').classList.contains('on')) startCam(); }, 3000);
    return;
  }
  pendingDev = r.device;
  $('sfName').textContent = pendingDev.name || '这台机器';
  $('sfSub').textContent  = deviceSub({ ...pendingDev, off: !pendingDev.online });
  scanHint('扫到了这台机器');
}""",
"""  Android.addDeviceFromCode(raw);      /* 结果从 window.onAddDevice 回来 */
}

window.onAddDevice = raw => {
  const r = fromNative(() => raw, '识别');
  if(!r.ok){
    $('pScan').classList.remove('done');
    scanHint((r.msg || '这台机器加不了') + ' —— 3 秒后重试');
    setTimeout(() => { if($('pScan').classList.contains('on')) startCam(); }, 3000);
    return;
  }
  pendingDev = r.device;
  $('sfName').textContent = pendingDev.name || '这台机器';
  $('sfSub').textContent  = deviceSub({ ...pendingDev, off: !pendingDev.online });
  scanHint('扫到了这台机器');
};""", '扫码识别改异步')

rep("""  const r = fromNative(() => Android.addDeviceByNo(no), '查询');
  if(!r.ok){ toast(r.msg || '这个号查不到机器'); return; }
  $('manual').classList.remove('on');
  pendingDev = r.device;
  $('sfName').textContent = pendingDev.name || '这台机器';
  $('sfSub').textContent  = deviceSub({ ...pendingDev, off: !pendingDev.online });
  $('pScan').classList.add('done');
  scanHint('查到了这台机器');
});""",
"""  Android.addDeviceByNo(no);           /* 结果从 window.onAddDeviceNo 回来 */
});

window.onAddDeviceNo = raw => {
  const r = fromNative(() => raw, '查询');
  if(!r.ok){ toast(r.msg || '这个号查不到机器'); return; }
  $('manual').classList.remove('on');
  pendingDev = r.device;
  $('sfName').textContent = pendingDev.name || '这台机器';
  $('sfSub').textContent  = deviceSub({ ...pendingDev, off: !pendingDev.online });
  $('pScan').classList.add('done');
  scanHint('查到了这台机器');
};""", '手动输号改异步')

rep("""  /* ⚠️ 解锁只开机器、**不出水**（出水靠人在机器上按按键）；
     但这个口子一开，之后谁按都记在本账号上。
     请求是阻塞的，先让出 80ms 把"正在解锁…"画出来，否则界面直接卡住。 */
  setTimeout(() => {
    const r = fromNative(() => Android.unlock(DEV[curDev].skuId), '解锁');
    if(!r.ok){
      busy = false;
      bUnlock.disabled = false;
      setLabel(bUnlock, '解锁设备');
      toast(r.msg || '解锁失败');
      return;
    }
    runUnlockAnim();
  }, 80);
});""",
"""  /* ⚠️ 解锁只开机器、**不出水**（出水靠人在机器上按）。结果从 window.onUnlock 回来。 */
  Android.unlock(DEV[curDev].skuId);
});

window.onUnlock = raw => {
  const r = fromNative(() => raw, '解锁');
  if(!r.ok){
    busy = false;
    bUnlock.disabled = false;
    setLabel(bUnlock, '解锁设备');
    toast(r.msg || '解锁失败');
    return;
  }
  runUnlockAnim();
};""", '解锁改异步')

rep("""function loadDevices(){
  DEV = (JSON.parse(Android.devices()) || []).map(d => ({ ...d, off: !d.online }));
  renderDevices();
}""",
"""function loadDevices(){
  /* 演示模式下绝不去问真实设备 —— 一问就会把 DEMO_DEV 顶掉。
     demoMode 以前只管解锁和账号显示，数据这块没人管，是个真漏洞：
     任何一条路径叫起 Android.devices()，演示数据就没了。 */
  if(demoMode) return;
  Android.devices();      /* 结果从 window.onDevices 回来（还要顺便问机器状态，会慢一点） */
}

window.onDevices = raw => {
  /* 双保险：万一有别的路径把真数据叫回来了，也不许覆盖演示数据。 */
  if(demoMode) return;
  DEV = (JSON.parse(raw) || []).map(d => ({ ...d, off: !d.online }));
  renderDevices();
  /* 卡片 CSS 默认 opacity:0，得靠 .in 才浮上来。DOM 是这里重建的，
     所以入场也得由这里发 —— 否则新卡片永远停在透明状态：
     DOM 里明明有，屏幕上一片空白。 */
  enterDev();
};""", '设备列表改异步（顺便带机器状态）')

# ── 接完水回来，订单和设备状态都得自己更新 ──
# 原来结束接水只重置了动画，订单页要杀进程重开才更新。
# 注意服务端那边结算是有一两秒延迟的，拉早了拿到的还是没有这笔新单的旧列表。
rep("""bEnd.addEventListener('click', () => {
  clearTimers();
  toast('已结束接水');
  at(260, reset);
});""",
"""bEnd.addEventListener('click', () => {
  bEnd.disabled = true;
  /* 界面先锁回去，不干等网络。 */
  clearTimers();
  at(260, reset);
  /* 等结算落地再拉，不然拉回来的是旧列表 */
  setTimeout(loadOrders, 1500);
  setTimeout(loadDevices, 1500);
  /* 但接口必须发 —— 不调这个，机器那头一直保持解锁态：
     界面上的锁是回去了，机器没锁。 */
  Android.endWater(DEV[curDev].goodsId);
});

window.onEndWater = raw => {
  const r = fromNative(() => raw, '结束接水');
  toast(r.ok ? '已结束接水' : (r.msg || '没通知到机器，你到机器上按一下'));
  /* 刚接过水，强制拉一次 —— 否则缓存没到期，新单子不会出现 */
  loadOrders(true);
};""", '结束接水：真调接口 + 刷新')

rep("""$('bk').addEventListener('click', () => {
  collapseTo(() => {
    $('pWater').classList.remove('on');
    $('pDev').classList.remove('hide');
    reset();
  });
});""",
"""$('bk').addEventListener('click', () => {
  collapseTo(() => {
    $('pWater').classList.remove('on');
    $('pDev').classList.remove('hide');
    reset();
  });
  /* 直接返回（没点「结束接水」）也强制刷一次 —— 水是接过了的 */
  loadOrders(true);
  loadDevices();
});""", '返回设备列表也刷新')

rep("""function showDevList(){
  setAcct();
  loadDevices();
  loadOrders();
  $('pLogin').classList.add('gone');
  setTimeout(() => { $('pLogin').style.display = 'none'; }, 320);
  setStep('dev');
  enterDev();
}""",
"""function showDevList(){
  setAcct();
  loadDevices();
  loadOrders();
  $('pLogin').classList.add('gone');
  setTimeout(() => { $('pLogin').style.display = 'none'; }, 320);
  setStep('dev');
  /* enterDev() 不摆在这儿 —— 卡片是 loadDevices 回调里才生成的（还要现问机器状态），
     在这里播入场，播的是个空气。 */
}""", '卡片入场交给 onDevices')

# ═══════════════════════ ⑰ 手机号格式：把一眼就不是手机号的挡在门外
# 起因：12345678910 配上胡编的验证码也能登进去（123 不是有效号段）。
#
# 根因在服务端：它对无效号段根本发不出短信，索性连验证码都不校验，
# 于是瞎编一个码就能登进一个空壳账号。官方 App 也一样不拦 ——
# RegexUtil.checkPhone 的正则 "(\\+\\d+)?(\\d{3,4}\\-?)?\\d{7,8}$" 比我们还松，
# 7~8 位纯数字就放行。
#
# 客户端堵不住服务端的洞，但至少能把「一眼就不是手机号」的挡在门口：
# 用完整的国内号段表，不是只卡第二位 —— 144 这种不存在的号段也得挡住。
# 这两处正则文本一模一样，rep 每次只换第一处，所以调两次。
for _k in range(2):
    rep("if(!/^1[0-9]{10}$/.test(phone)){ toast('手机号填完整'); return; }",
        "if(!/^1(3\\d|4[5-9]|5[0-35-9]|6[2567]|7[0-8]|8\\d|9\\d)\\d{8}$/.test(phone)){ quip('这不像个手机号'); return; }",
        '手机号正则收紧 + 改走气泡 %d' % (_k + 1))

# 验证码长度那两条也改走气泡。
# 用 rep 而不是把上面改掉 —— 上面那两条 newText 是分层的，
# 动其中一层会连带把后续 rep 的 oldText 全弄不匹配。
rep("if(code.length < 4){ toast('验证码填完整'); return; }",
    "if(code.length < 4){ quip('验证码填完整'); return; }", '验证码校验改走气泡')

# ═══════════════════════ ⑱ 开发期：跳过登录先看界面
# 需求：开发期间不用等登录就能看界面、调样式。
#
# 这里本来还有一个「开发者通道」面板，用来粘贴 token —— **已经删了**。
# 理由是它压根没用：换 token 必须用短信验证码，而验证码只在他手机上。
# 所谓「我在电脑侧拿到 token 给你粘」的流程是：
#   他收码 → 告诉我 → 我登录 → 我把 token 给他 → 他粘
# 比他直接在 App 里输码还多绕两步。自找麻烦。
#
# ⚠️ 演示模式下绝不能碰到真机解锁 —— 解锁后机器谁按都记这个账号，一样是真金白银。见下面 unlock / endWater 两处。

# ③ 逻辑：长按水滴 1.4 秒唤起
rep("""window.onCamReady = ok => {""",
"""/* ══ 演示模式：假数据跑界面，一个接口都不碰 ══ */
let demoMode = false;

const DEMO_DEV = [
  { goodsId:'demo1', name:'1号楼西热水', price:'0.011', skuId:'demo1', online:true,  workStatus:1 },
  { goodsId:'demo2', name:'2号楼东热水', price:'0.011', skuId:'demo2', online:true,  workStatus:3 },
  { goodsId:'demo3', name:'楼下那台',     price:'',      skuId:'demo3', online:false, workStatus:0 }
];
const DEMO_ORDER = [
  { orderNo:'d1', dev:'1号楼西热水', amt:0.25, time:'07:12', dateKey:'2026-03-18', date:'3月18日' },
  { orderNo:'d2', dev:'2号楼东热水', amt:0.99, time:'07:10', dateKey:'2026-03-18', date:'3月18日' },
  { orderNo:'d3', dev:'1号楼西热水', amt:2.30, time:'12:31', dateKey:'2026-03-17', date:'3月17日' },
  { orderNo:'d4', dev:'1号楼西热水', amt:0.76, time:'20:02', dateKey:'2026-03-17', date:'3月17日' },
  { orderNo:'d5', dev:'1号楼西热水', amt:1.85, time:'21:04', dateKey:'2026-03-15', date:'3月15日' }
];

function enterDemo(){
  demoMode = true;
  DEV = DEMO_DEV.map(d => ({ ...d, off: !d.online }));
  renderDevices();
  setOrders(DEMO_ORDER);
  $('pOrder').classList.toggle('empty', ORDERS.length === 0);
  buildOrders();
  buildSummary();
  $('acct').textContent = '演示模式';
  $('acctNo').textContent = '演示模式 · 不是真数据';
  $('pLogin').classList.add('gone');
  setTimeout(() => { $('pLogin').style.display = 'none'; }, 320);
  setStep('dev');
  enterDev();
}

window.onCamReady = ok => {""", '演示模式')

# ④ 演示模式下不许碰真机解锁（解锁后谁按机器都记本账号）
rep("""  /* ⚠️ 解锁只开机器、**不出水**（出水靠人在机器上按）。结果从 window.onUnlock 回来。 */
  Android.unlock(DEV[curDev].skuId);""",
"""  /* ⚠️ 解锁只开机器、**不出水**（出水靠人在机器上按）；口子一开谁按都记本账号。
     演示模式下绝对不能走到这儿 —— 解锁了机器，之后谁按都算这个账号。 */
  if(demoMode){
    setTimeout(() => window.onUnlock && window.onUnlock('{"ok":true,"orderNo":"DEMO"}'), 300);
  } else {
    Android.unlock(DEV[curDev].skuId);
  }""", '演示模式不碰真解锁')

rep("""  Android.endWater(DEV[curDev].goodsId);""",
"""  if(!demoMode) Android.endWater(DEV[curDev].goodsId);""", '演示模式不碰结束接口')

# ⑤（原来这里有一条「返回键先收开发者面板」，面板删了，用不上了）

rep("""function setAcct(){
  const p = (Android.phone && Android.phone()) || '';""",
"""function setAcct(){
  if(demoMode){                       /* 演示模式别显示真号 */
    $('acct').textContent = '演示模式';
    $('acctNo').textContent = '演示模式 · 不是真数据';
    return;
  }
  const p = (Android.phone && Android.phone()) || '';""", '演示模式不改账号显示')

# ⑥ （原来这里往登录页底部塞了一个「先看界面（不用登录）」按钮）
#    删了。开发期通道放在界面上太昭彰 —— 现在改成彩蛋：
#    连点水滴 15 下，炸开进演示模式。见上面「彩蛋：点水滴」那段。

# ═══════════════════════ ⑲ 手感：记住手机号 + 启动时水滴长大
# 「边缘滑动返回」没做：这台机器的 navigation_mode = 2（全面屏手势），
# 系统已经把左右边缘占了 —— 我们再监听会跟它打架，白搭。

rep("""setStep('login');
/* 已经登录过就直接进列表，不用每次重登一遍。""",
"""/* 上次登录过的号自动填上 —— 每天重输 11 位数字太烦 */
try {
  const last = (Android.phone && Android.phone()) || '';
  if(last && !$('iPhone').value) $('iPhone').value = last;
} catch(e){}

/* 水滴先从一个点长出来（原来一进来就是完整一个） */
const pwrap = document.querySelector('.plwrap');
if(pwrap){
  pwrap.classList.add('birth');
  /* 播完就撤 class，好让原本的 breathe 呼吸动画接着跑 */
  setTimeout(() => pwrap.classList.remove('birth'), 820);
}

setStep('login');
/* 已经登录过就直接进列表，不用每次重登一遍。""", '登录页预填 + 水滴长大')

rep('</style>', """  /* ═══ 启动时水滴长大 ═══ */
  @keyframes dropBirth{
    0%  { transform:scale(.18) translateY(26px); opacity:0 }
    62% { transform:scale(1.07) translateY(0);   opacity:1 }
    82% { transform:scale(.98) }
    100%{ transform:scale(1) }
  }
  .plwrap.birth{ animation:dropBirth .68s var(--spring) both }
""" + '</style>', '水滴长大动画')

# ═══════════════════════ ⑳ 空列表不再是一句干吧吧的灰字
rep("""  if(!DEV.length){
    box.innerHTML = '<div class="dempty">还没有设备<br>点右上角的 + 扫机器上的二维码</div>';
    return;
  }""",
"""  if(!DEV.length){
    /* 一只水滴 + 一个按钮 —— 比一句灰字友好 */
    box.innerHTML =
      '<div class="dempty">' +
        '<svg class="dhint" viewBox="0 0 76 100">' +
          '<path d="M 38 6 C 46 32 68 44 68 62 C 68 82 54 94 38 94 C 22 94 8 82 8 62 C 8 44 30 32 38 6 Z"/>' +
        '</svg>' +
        '<div class="dempty-t">还没有设备</div>' +
        '<button class="btn fill dadd" id="bEmptyAdd" type="button"><span class="lb">扫一扫</span></button>' +
      '</div>';
    setTimeout(() => {
      const b = $('bEmptyAdd');
      if(b) b.addEventListener('click', () => Android.askCamera());
    }, 0);
    return;
  }""", '空列表插画 + 扫一扫按钮')

rep('</style>', """  .dhint{ width:34px; height:45px; fill:#EADCD8 }
  .dempty-t{ margin-top:12px }
  .dadd{ margin-top:16px; width:150px }
""" + '</style>', '空列表样式')

# ═══════════════════════ ㉑ 设备卡片：长按拖动排序 / 左滑删除 / 确认后粉碎
#
# 需求：
#   · 长按卡片 → 震一下 → 卡片浮起来跟手挪，松手落位并把顺序写回本地
#   · 卡片向左滑 → 露出删除按钮 → 点它弹确认 → 确认后卡片粉碎消失
#   · 动效照 docs/MOTION.md 那套 yui540 手法：只动 transform/opacity，
#     出现慢消失快，挤压要横竖比例不同，多层错时有纵深，拖拽时 transition:none
#
# 一个坑先说在前面：现成的 .dcard.in 是用 transform 做入场位移的，而拖动/左滑也要写
# transform —— 会打架。解法是交互期间 JS 直接写 inline style（inline 优先于 class），
# 并且给卡片加 .gesturing 把 transition 关掉（MOTION.md 第 7 条：拖拽时必须无过渡）。

# ① 样式
CARD_GESTURE_CSS = u'''
  /* ═══ 设备卡片：左滑 / 拖动 / 粉碎 ═══ */
  /* dwrap 只管滑动窗口、裁切和「整条塌陷」。
     ★ 背景和阴影不能挂在这层 —— 卡片让位是 translateY，上移的那张会钻进被拖
     那一条的区域里，dwrap 如果自己有白底就会把它整个盖住（表现就是「其他卡片
     都不见了」）。让卡片带着自己的底走。 */
  .dwrap{
    position:relative; border-radius:18px; margin-bottom:10px;
    transition:height .24s cubic-bezier(.87,.05,.02,.97), opacity .16s linear;
  }
  /* 裁切只在「滑动中 / 已滑开 / 收回动画中」生效。
     不能常开：拖动让位时卡片被 translateY 挪出容器，一裁整块内容就没了。
     铺的那层底色是挡卡片底边抗锯齿透出来的 1px 红。 */
  .dwrap.swiping, .dwrap.swiped, .dwrap.closing{
    clip-path:inset(0 round 18px);
    background:var(--bg);
  }
  .dwrap.dragging{ z-index:10 }
  .dwrap.shiver{ animation:shiver .19s ease-in-out infinite alternate }

  /* 卡片底下的删除按钮 —— 卡片往左一滑就露出来 */
  .ddel{
    position:absolute; right:0; top:1px; bottom:1px;
    /* 红色必须一直填满「卡片右边缘 → 容器右边」，中间不能留缝，
       所以宽度跟着滑动量走：106 = 88(按钮本身) + 18(藏到卡片底下)。
       多伸出去的那 18px 被卡片盖住，正好把卡片右圆角挖掉的两个「月牙」补满 ——
       看上去就是红色从卡片的圆角里咬进去。 */
    width:calc(var(--sx, 0px) + 106px);
    border:0; border-radius:0 18px 18px 0;
    background:#E24B3F; color:#fff;
    font:inherit; font-size:13px; font-weight:650; letter-spacing:.08em; cursor:pointer;
    opacity:var(--rv,0);              /* ★ 静止时恒为 0，进门那一眼看不到它 */
    transition:opacity .1s linear, background .15s linear;
  }
  .ddel:active{ background:#C93A2F }
  /* 「删除」两个字必须钉在「露出来的那块红色」的正中间 —— 红块会随滑动变宽，
     文字不能跟着整块居中，那样滑得越远它越往左，最后被卡片盖掉。 */
  .ddel span{
    position:absolute; right:0; top:0; bottom:0;
    width:max(88px, var(--sx, 0px));
    display:flex; align-items:center; justify-content:center;
    /* 「删除」自己的皮筋：滑过 DEL_W 之后继续拉，字被横向拉长、纵向压细 */
    transform:scaleX(var(--del-x, 1)) scaleY(var(--del-y, 1));
    transition:transform .3s var(--spring);
  }
  .dwrap.swiping .ddel span{ transition:none }

  /* 皮筋：左滑时把内容横向拉长、纵向压细。origin 放左边 ——
     卡片是被右边「拽」着走的，左端该钉住不动。 */
  .dcard .dshake{
    display:flex; align-items:center; gap:13px; flex:1; min-width:0;
    transform-origin:left center;
    transform:scaleX(var(--sx-x, 1)) scaleY(var(--sx-y, 1));
    transition:transform .3s var(--spring);
  }
  .dcard.gesturing .dshake{ transition:none }

  .dcard{ margin-bottom:0; border-radius:18px; overflow:hidden }
  /* 手指还在屏幕上时不要过渡，要绝对跟手 */
  .dcard.gesturing{ transition:none !important }
  .dcard.lifted{
    position:relative; z-index:5;
    box-shadow:0 12px 28px rgba(143,76,56,.22);
  }

  /* 确认弹窗里那个红按钮 */
  .btn.danger{ background:#E24B3F; color:#fff }
  .btn.danger:active{ background:#C93A2F }

  /* 粉末 —— 整条（卡片 + 红色删除键）碎成的粒子。
     主流做法是像素采样（元素画进 canvas、读每个像素的颜色、按密度抽样成粒子），
     这里不引 canvas，改成「网格采样 + 按位置判色」。 */
  .dust{
    position:absolute; width:2.6px; height:2.6px; border-radius:50%;
    pointer-events:none; z-index:6;
    filter:blur(.25px);                /* 一点点柔化，不然是「点阵」不是「粉末」 */
    animation:dustFly .78s cubic-bezier(.64,.08,1,.97) both;
  }
  @keyframes dustFly{
    0%  { transform:translate(0,0) scale(1) rotate(0deg); opacity:1 }
    55% { opacity:.9 }
    100%{ transform:translate(var(--dx,0), var(--dy,60px)) scale(.35) rotate(var(--rz,0deg)); opacity:0 }
  }

  /* 抖的是整张卡片，不是卡片里的字。抖 dwrap 不抖 dcard ——
     dcard 的 transform 已经被拖动位移占了，压一起会打架。 */
  @keyframes shiver{
    from{ transform:rotate(-.85deg) scale(1.012) }
    to  { transform:rotate( .85deg) scale(1.012) }
  }
'''
rep('</style>', CARD_GESTURE_CSS + '</style>', '卡片手势样式')

# ② 确认弹窗（复用 .manual 那套浮层）
rep('''      <div class="manual" id="acctBox">''',
'''      <div class="manual" id="rmBox">
        <div class="mbox">
          <div class="mt">删掉这台设备？</div>
          <div class="ms"><span id="rmName">这台设备</span></div>
          <button class="btn danger" id="bRmOk" type="button"><span class="lb">删掉它</span></button>
          <button class="btn dark" id="bRmNo" type="button"><span class="lb">算了</span></button>
        </div>
      </div>

      <div class="manual" id="acctBox">''', '删除确认弹窗')

# ③ 手势逻辑
CARD_GESTURE_JS = u'''/* ══ 设备卡片：长按拖动排序 / 左滑删除 / 粉碎 ══ */
const HOLD_MS = 420;      /* 多久算长按 */
const SLOP_PX = 9;        /* 位移超过它就不算“按”而是“滑”了 */
const DEL_W   = 88;       /* 删除按钮宽度，要跟 CSS 里那个 88px 一致 */
const OPEN_PX = 80;       /* 左滑超过它就贴住不弹回 */
const FLY_WAIT = 260;     /* 粒子飞多久后整条塌陷，下面的卡片跟着收上去 */

let G = null;             /* 当前手势 */
let pendingRemove = -1;   /* 等确认的那一台 */

/* 一次把「红色露多少 / 皮筋拉多长」都算好。v 是卡片的 translateX（负 = 左滑） */
function setSlide(wrap, v){
  const sx = Math.max(0, -v);                     /* 左滑距离，正值 */
  wrap.style.setProperty('--sx', sx + 'px');
  wrap.style.setProperty('--rv', Math.max(0, Math.min(1, sx / DEL_W)));
  /* 卡片皮筋：只认左滑，右滑（回弹过冲）时 sx 恒为 0，拉伸自然回到 1 */
  const k = Math.min(sx / DEL_W, 1.15);
  wrap.style.setProperty('--sx-x', (1 + k * 0.105).toFixed(3));
  wrap.style.setProperty('--sx-y', (1 - k * 0.042).toFixed(3));
  /* 「删除」两个字的皮筋：滑过 DEL_W（红色刚好铺满）之后，继续滑多少就拉多长 */
  const over = Math.max(0, sx - DEL_W);
  const t = Math.min(over / 50, 1);
  wrap.style.setProperty('--del-x', (1 + t * 0.6).toFixed(3));
  wrap.style.setProperty('--del-y', (1 - t * 0.18).toFixed(3));
}

/* 收回时把裁切留住，等回弹动画走完再摘 —— 不然卡片带惯性往右冲的那一下
   会冲出容器边界，露出一个方角。 */
function holdClip(wrap, ms){
  wrap.classList.add('closing');
  clearTimeout(wrap._clipT);
  wrap._clipT = setTimeout(() => wrap.classList.remove('closing'), ms);
}

function closeSwipe(wrap){
  const card = wrap.querySelector('.dcard');
  if(card) card.style.transform = '';
  setSlide(wrap, 0);
  holdClip(wrap, 360);
  wrap.classList.remove('swiped', 'swiping');
}

/* ★ 点屏幕上任何地方都收回。捕获阶段拦下来，并且 preventDefault + stopPropagation ——
   iOS 原生的行为就是「点外面的那一下只用来关闭，绝不穿透到下面」，
   否则用户想关掉滑动结果却误触了别的卡片。 */
document.addEventListener('pointerdown', e => {
  const open = document.querySelector('.dwrap.swiped');
  if(!open) return;
  if(e.target.closest('.ddel')) return;            /* 点删除键：交给它自己 */
  if(e.target.closest('.manual')) return;          /* 弹窗开着时别动卡片 */
  /* 点这张卡片本身也在这里就收回 —— 别丢给 click，
     click 要等 pointerup 之后才发，会比点别处慢一拍。 */
  if(e.target.closest('.dwrap.swiped')){ closeSwipe(open); return; }
  e.preventDefault();
  e.stopPropagation();
  closeSwipe(open);
}, true);

function bindCardGestures(wrap){
  const card = wrap.querySelector('.dcard');

  wrap.addEventListener('touchstart', e => {
    if(e.touches.length !== 1) return;
    if(wrap.classList.contains('swiped')) return;   /* 已滑开的，先让它自己收回去 */
    const t = e.touches[0];
    G = { wrap:wrap, card:card, i:+wrap.dataset.d, x0:t.clientX, y0:t.clientY,
          mode:'idle', dx:0, dy:0, shift:0, h:0, cards:null };
    G.holdT = setTimeout(() => {
      if(!G || G.mode !== 'idle') return;
      G.mode = 'drag';
      Android.buzz(22);                              /* 震一下：拿起来了 */
      startDrag(G);
    }, HOLD_MS);
  }, { passive:true });

  wrap.addEventListener('touchmove', e => {
    if(!G) return;
    const t  = e.touches[0];
    const dx = t.clientX - G.x0, dy = t.clientY - G.y0;

    if(G.mode === 'idle'){
      if(Math.abs(dx) > SLOP_PX && Math.abs(dx) > Math.abs(dy)){
        clearTimeout(G.holdT);
        G.mode = 'swipe';
        G.card.classList.add('gesturing');
      } else if(Math.abs(dy) > SLOP_PX){
        clearTimeout(G.holdT);
        G.mode = 'scroll';                           /* 让页面自己滚 */
      }
    }

    if(G.mode === 'swipe'){
      let v = dx;
      if(v > 0) v *= 0.16;                                   /* 往右：阻力，别硬拖 */
      if(v < -DEL_W - 30) v = -DEL_W - 30 + (v + DEL_W + 30) * 0.22;
      G.dx = v;
      G.card.style.transform = 'translateX(' + v.toFixed(1) + 'px)';
      setSlide(G.wrap, v);
      e.preventDefault();
    }

    if(G.mode === 'drag') dragMove(G, t.clientY);
  }, { passive:false });

  wrap.addEventListener('touchend', e => {
    if(!G) return;
    clearTimeout(G.holdT);
    const g = G; G = null;

    if(g.mode === 'swipe'){
      g.card.classList.remove('gesturing');
      const open = g.dx < -OPEN_PX;
      g.card.style.transform = open ? 'translateX(-' + DEL_W + 'px)' : '';
      setSlide(g.wrap, open ? -DEL_W : 0);
      g.wrap.classList.toggle('swiped', open);
      g.wrap.classList.remove('swiping');
      holdClip(g.wrap, 360);                           /* 回弹那一下也要裁住 */
      if(g.dx !== 0) e.preventDefault();               /* 滑过就不算点击 */
    } else if(g.mode === 'drag'){
      endDrag(g);
      e.preventDefault();
    }
    /* 还是 idle 的，交给 click 去 openWater */
  }, { passive:false });

  wrap.addEventListener('touchcancel', () => {
    if(!G) return;
    clearTimeout(G.holdT);
    const g = G; G = null;
    if(g.mode === 'drag'){
      /* 触摸被系统打断（来电、切后台）—— 位移必须清干净，否则卡片卡在半路 */
      g.card.classList.remove('gesturing','lifted');
      g.wrap.classList.remove('dragging','shiver');
      ;[].slice.call(document.querySelectorAll('.dcard')).forEach(c => { c.style.transform = ''; });
      renderDevices();
    }
    if(g.mode === 'swipe'){
      g.card.classList.remove('gesturing');
      closeSwipe(g.wrap);
    }
  }, { passive:true });

  card.addEventListener('click', () => {
    if(wrap.classList.contains('swiped')){ closeSwipe(wrap); return; }
    openWater(+wrap.dataset.d);
  });

  wrap.querySelector('.ddel').addEventListener('click', e => {
    e.stopPropagation();
    const i = +wrap.dataset.d;
    if(!DEV[i]) return;
    pendingRemove = i;
    $('rmName').textContent = DEV[i].name || '这台设备';
    $('rmBox').classList.add('on');
  });
}

function startDrag(g){
  const r = g.card.getBoundingClientRect();
  g.h = r.height + 10;                    /* 卡片高 + 间距，和 .dwrap 的 margin 一致 */
  g.cards = [].slice.call(document.querySelectorAll('.dcard'));
  g.card.classList.add('gesturing', 'lifted');
  g.wrap.classList.add('dragging', 'shiver');      /* ★ 抖的是整条卡片 */
  g.card.style.transform = 'scale(1.028)';          /* 拿起来：稍微浮一点 */
}

function dragMove(g, cy){
  const dy = cy - g.y0;
  g.card.style.transform =
    'translateY(' + dy.toFixed(1) + 'px) scale(1.028)';

  /* 往下挪一格就换一位。夹住范围，别飞出列表 */
  let shift = Math.round(dy / g.h);
  shift = Math.max(-g.i, Math.min(g.cards.length - 1 - g.i, shift));
  g.shift = shift;

  /* 被挤的卡片往反方向挪一格 —— 就一格，不用管它们各自的原始位置 */
  g.cards.forEach((c, j) => {
    if(c === g.card) return;
    let t = 0;
    if(shift > 0 && j > g.i && j <= g.i + shift) t = -g.h;
    if(shift < 0 && j < g.i && j >= g.i + shift) t =  g.h;
    c.style.transform = t ? 'translateY(' + t + 'px)' : '';
  });
}

function endDrag(g){
  g.card.classList.remove('gesturing', 'lifted');
  g.wrap.classList.remove('dragging', 'shiver');
  const moved = g.shift !== 0;

  if(moved){
    const item = DEV.splice(g.i, 1)[0];
    DEV.splice(g.i + g.shift, 0, item);
    /* 演示模式只动内存 —— 别把假设备的顺序写进真实存储 */
    if(!demoMode) Android.reorderDevices(JSON.stringify(DEV.map(d => d.goodsId)));
  }

  /* 收尾前先把手写过的 transform 全清掉 —— 不清的话下面那些卡片会从
     错误的位置滑回去，看着像挫了一下。 */
  ;[].slice.call(document.querySelectorAll('.dcard')).forEach(c => { c.style.transform = ''; });

  if(moved){ renderDevices(); }
  else { g.card.style.transform = ''; }   /* 没挪动：让它弹回原处，不重渲染（重渲染会闪） */
}

/* 粉末：整条（卡片 + 红色删除键）一起化成粒子，不会剩下一块红在后面。
   每个粒子按它「原来的位置」取色 —— 这是网上那套像素采样的简化版：
   主流做法是把元素画进 canvas、读每个像素的颜色、按密度抽样成粒子
   （Disintegrate.js / snap-vanish / Thanos 那套）；这里不引 canvas，
   改成「网格采样 + 按位置判色」。 */
function crushWrap(wrap, done){
  const card = wrap.querySelector('.dcard');
  const W = wrap.offsetWidth, H = wrap.offsetHeight;
  const cr = card.getBoundingClientRect(), wr = wrap.getBoundingClientRect();
  const cardR = cr.left - wr.left + cr.width;   /* 卡片右边缘；它右边就是露出的删除键 */

  card.style.opacity = '0';
  wrap.style.setProperty('--rv', 0);            /* 红块也淡掉，由红色粒子接手 */
  wrap.style.clipPath = 'none';                 /* 粒子要飞出容器，别裁 */
  wrap.style.zIndex = 20;                       /* 也别被后面那张卡片盖住 */

  const COLS = 20, ROWS = 8;                    /* 160 个 —— 少了是「点阵」，多了才像粉末 */
  for(let r = 0; r < ROWS; r++){
    for(let c = 0; c < COLS; c++){
      /* 位置加一点抖动，不然整齐的网格看着像「点阵」不像「粉末」 */
      const x = (c + 0.5 + (Math.random() - 0.5) * 0.9) / COLS * W;
      const y = (r + 0.5 + (Math.random() - 0.5) * 0.9) / ROWS * H;

      let bg;
      if(x > cardR) bg = '#E24B3F';                          /* 删除键：红 */
      else if(y > H * 0.2 && y < H * 0.8) bg = '#C6B4AE';    /* 文字带：深一点 */
      else bg = '#F2E8E3';                                   /* 卡片底 */

      const p = document.createElement('i');
      p.className = 'dust';
      p.style.left = x.toFixed(1) + 'px';
      p.style.top  = y.toFixed(1) + 'px';
      p.style.background = bg;

      const cx = x / W - 0.5, cy = y / H - 0.5;
      const out = Math.hypot(cx, cy) / 0.7;                  /* 0 = 中心，1 = 角 */
      p.style.setProperty('--dx', (cx * 152 + (Math.random() * 42 - 21)).toFixed(0) + 'px');
      p.style.setProperty('--dy', (cy * 74 + 44 + Math.random() * 54).toFixed(0) + 'px');
      p.style.setProperty('--rz', ((Math.random() - 0.5) * 44).toFixed(0) + 'deg');
      /* 中心的先散，往外扩；递增排延迟会像音阶一样机械 */
      p.style.animationDelay = (Math.random() * 56 + out * 76).toFixed(0) + 'ms';

      wrap.appendChild(p);
    }
  }

  /* 整条塌陷，下面的卡片跟着收上去。
     这里不把 wrap 的 opacity 打成 0 —— 那会把还在飞的粒子一起抹掉。 */
  setTimeout(() => {
    wrap.style.height = wrap.offsetHeight + 'px';
    void wrap.offsetWidth;                       /* 强制回流，否则 height 起点不生效 */
    wrap.style.height = '0px';
  }, FLY_WAIT);

  setTimeout(() => { if(done) done(); }, 1000);
}

$('bRmOk').addEventListener('click', () => {
  $('rmBox').classList.remove('on');
  const i = pendingRemove; pendingRemove = -1;
  if(i < 0 || !DEV[i]) return;
  const wrap = document.querySelector('.dwrap[data-d="' + i + '"]');
  const goodsId = DEV[i].goodsId;
  DEV.splice(i, 1);
  /* 演示模式：只动内存，不碰 DeviceStore 也不发请求 */
  if(!demoMode) Android.removeDevice(goodsId);
  if(wrap) crushWrap(wrap, () => renderDevices());
  else renderDevices();
});

$('bRmNo').addEventListener('click', () => {
  $('rmBox').classList.remove('on');
  pendingRemove = -1;
});

'''
rep('''window.onCamReady = ok => {''', CARD_GESTURE_JS + '''window.onCamReady = ok => {''',
    '卡片手势逻辑')

# ④ 卡片改成两层：外层滑动窗口 + 内层卡片
rep('''  box.innerHTML = DEV.map((d, i) =>
    '<div class="dcard" data-d="' + i + '">' +
      '<span class="dot' + (d.off ? ' off' : '') + '"></span>' +
      '<div class="i"><div class="n">' + esc(d.name || '未命名设备') + '</div>' +
      '<div class="p">' + esc(deviceSub(d)) + '</div></div>' + ARROW +
    '</div>').join('');
  box.querySelectorAll('.dcard').forEach(c => {
    c.addEventListener('click', () => openWater(+c.dataset.d));
  });''',
'''  box.innerHTML = DEV.map((d, i) =>
    '<div class="dwrap" data-d="' + i + '">' +
      '<button class="ddel" type="button"><span>删除</span></button>' +
      '<div class="dcard" data-d="' + i + '">' +
        '<div class="dshake">' +
          '<span class="dot' + (d.off ? ' off' : '') + '"></span>' +
          '<div class="i"><div class="n">' + esc(d.name || '未命名设备') + '</div>' +
          '<div class="p">' + esc(deviceSub(d)) + '</div></div>' +
        '</div>' + ARROW +
      '</div>' +
    '</div>').join('');
  box.querySelectorAll('.dwrap').forEach(bindCardGestures);

  /* 卡片 CSS 默认 opacity:0，靠 .in 才浮上来。DOM 是这里重建的，所以必须在这里
     补一次 —— 否则排序/删除之后列表会整片空白（卡片看不见，只剩底下那个红色
     删除按钮露在外面）。需要错落入场的地方（进列表、拉数据）交给 enterDev()：
     它会把 .in 摘掉重放，所以两边不会打架。 */
  requestAnimationFrame(() => {
    box.querySelectorAll('.dcard').forEach(c => c.classList.add('in'));
  });''',
    '卡片改成滑动窗口 + 手势绑定')

# ═══════════════════════ ㉒ 订单页：年份滚轮
# 月份头全去掉（日期自带「3月18日」，上面再顶一个是重复的），
# 右边换成可上下滑的年份滚轮，滑到哪年出哪年的小计和流水。

# ── CSS：顶部改成左右两栏，加滚轮，删掉 7 天柱 ──
rep("""  .osum{ padding-top:2px }
  .oamt{ display:flex; align-items:baseline }""",
"""  /* 顶部：左边总额，右边年份滚轮 */
  .osum{ display:flex; align-items:flex-start; gap:10px; padding-top:2px }
  .osum .left{ flex:1; min-width:0 }
  .oamt{ display:flex; align-items:baseline }""", '订单顶部改两栏')

rep("""  /* 7 天柱 */
  .obars{ display:flex; align-items:flex-end; gap:5px; height:24px; margin-top:10px }
  .obars > i{ display:block; width:5px; height:24px; border-radius:3px; background:#E2D3CE;
              transform:scaleY(var(--h)); transform-origin:bottom }
  .obars > i.hi{ background:var(--primary) }""",
"""  /* 年份滚轮 —— 用原生 scroll-snap，惯性和吸附都是系统的，
     不用自己算位移。上下各垫一格，第一个和最后一个才滚得到正中间。 */
  .oyears{ flex:none; width:62px; height:88px; overflow-y:auto;
           scroll-snap-type:y mandatory; scrollbar-width:none;
           -webkit-overflow-scrolling:touch; overscroll-behavior:contain;
           -webkit-mask-image:linear-gradient(to bottom, transparent, #000 26%, #000 74%, transparent);
                   mask-image:linear-gradient(to bottom, transparent, #000 26%, #000 74%, transparent) }
  .oyears::-webkit-scrollbar{ display:none }
  .oyears .pad{ height:44px }
  .oyears b{ display:flex; align-items:center; justify-content:flex-end; gap:4px;
             height:44px; scroll-snap-align:center; font-size:15px; font-weight:650;
             color:#CBB6AF; cursor:pointer; user-select:none;
             transform:scale(1); transform-origin:right center;
             transition:color .24s linear, transform .32s var(--spring) }
  .oyears b .yr{ font-size:10.5px; font-weight:600; opacity:.7 }
  .oyears b.on{ color:var(--primary); transform:scale(1.14) }

  /* 当年小计 */
  .oysum{ display:flex; align-items:baseline; gap:8px; padding:12px 0 8px; margin-top:10px;
          border-bottom:1px solid var(--outline-var) }
  .oysum .y{ font-size:13px; font-weight:750; color:var(--primary) }
  .oysum .days{ font-size:11px; color:#A08E88 }
  .oysum .tot{ margin-left:auto; font-size:13.5px; font-weight:750; color:var(--primary);
               font-variant-numeric:tabular-nums; height:17px; overflow:hidden; line-height:17px }
  .oysum .tot > u{ display:block; text-decoration:none }
  .oysum.out{ animation:oOut .15s linear both }""", '7 天柱换成年份滚轮')

# 日期行改成 flex，右边腾出当日合计的位置
rep("""  .odate{ position:relative; padding:11px 0 5px 26px; font-size:11.5px; font-weight:650;
          color:var(--primary) }""",
"""  .odate{ position:relative; display:flex; align-items:baseline; gap:9px;
          padding:11px 0 5px 26px; font-size:11.5px; font-weight:650;
          color:var(--primary) }
  /* 当日合计 —— 扫一眼就知道这天花了多少（Monzo 的手法） */
  .odate .dsum{ margin-left:auto; font-size:11.5px; font-weight:650; color:#B49A93;
                font-variant-numeric:tabular-nums;
                height:14px; overflow:hidden; line-height:14px }
  .odate .dsum > u{ display:block; text-decoration:none }
  .odate .dsum > u{ animation:oLand .5s var(--spring) both; animation-delay:var(--dl) }""",
    '日期行加当日合计')

# 删掉 7 天柱的入场动画，换成滚轮项的
rep("""  #pOrder.play .obars > i{ animation:oGrow .46s var(--spring) both; animation-delay:var(--dl) }""",
"""  #pOrder.play .oyears b{ animation:oSlide .5s var(--spring) both; animation-delay:var(--dl) }
  #pOrder.play .oysum  { animation:oRow .5s var(--spring) both; animation-delay:var(--dl) }
  #pOrder.play .oysum .tot > u{ animation:oLand .5s var(--spring) both; animation-delay:var(--dl) }""",
    '动画换成滚轮')

rep("""  @keyframes oDraw{ from{ transform:scaleY(0) } }""",
"""  @keyframes oDraw{ from{ transform:scaleY(0) } }
  @keyframes oSlide{ from{ opacity:0; transform:translateX(12px) scale(.9) } }
  @keyframes oOut{ to{ opacity:0; transform:translateY(-5px) } }
  /* 换年时旧内容快出 */
  #pOrder .tl.out{ animation:oOut .15s linear both }""", '换年的出入场')

# ── HTML：加滚轮 + 当年小计 ──
rep("""        <div class="osum">
          <div class="oamt"><span class="ocur">&yen;</span><span class="onum" id="oNum"></span></div>
          <div class="osub" id="oSub">本月 · 7 天 · 0 单</div>
          <div class="obars" id="oBars"></div>
        </div>
        <div class="tl" id="oTl"></div>""",
"""        <div class="osum">
          <div class="left">
            <div class="oamt"><span class="ocur">&yen;</span><span class="onum" id="oNum"></span></div>
            <div class="osub" id="oSub"></div>
          </div>
          <div class="oyears" id="oYears"></div>
        </div>
        <div class="oysum" id="oYsum"></div>
        <div class="tl" id="oTl"></div>""", '订单页 HTML 加滚轮')

# ── JS：重写 buildOrders，加年份滚轮 ──
rep("""function buildOrders(){
  const flat = [];
  ORDERS.forEach(g => g.items.forEach(it => flat.push({ ...it, date:g.date })));
  FLAT.length = 0; flat.forEach((it, i) => FLAT.push({ ...it, i }));

  const sum = Math.round(flat.reduce((a, b) => a + b.amt, 0) * 100) / 100;

  /* 金额：每一位一个滑窗，延迟 0/50/100… 逐位落下 */
  const num = $('oNum'); num.innerHTML = '';
  [...sum.toFixed(2)].forEach((ch, i) => {
    const el = document.createElement('i');
    el.style.setProperty('--dl', (i * 50) + 'ms');
    const u = document.createElement('u');
    u.textContent = ch;
    el.appendChild(u); num.appendChild(el);
  });
  $('oSub').textContent = '本月 · ' + ORDERS.length + ' 天 · ' + flat.length + ' 单';

  /* 近 7 天柱：从明细里算出来。写死的话，一换成真订单立刻就对不上。
     只按天分组不认日历 —— 缺的那天补 0，所以柱子数永远是 7。 */
  const dayTot = ORDERS.slice().reverse().map(g => g.items.reduce((a, b) => a + b.amt, 0));
  while(dayTot.length < 7) dayTot.unshift(0);
  const last7 = dayTot.slice(-7);
  const mx = Math.max.apply(null, last7.concat([0.01]));
  $('oBars').innerHTML = last7.map((v, i) => {
    const h = Math.round(v / mx * 100) / 100;
    return '<i style="--h:' + Math.max(h, .04) + ';--dl:' + DLD[i] + 'ms" class="' +
             (h === 1 ? 'hi' : '') + '"></i>';
  }).join('');

  /* 时间轴：竖线自己画下来，日期节点一个个亮，行跟着错落 */
  let t = 280, k = 0, html = '<div class="spine"></div>';
  ORDERS.forEach(g => {
    html += '<div class="odate"><i style="--dl:' + t + 'ms"></i>' + g.date + '</div>';
    t += 150;
    g.items.forEach(it => {
      html += '<div class="orow" data-i="' + (k++) + '" style="--dl:' + t + 'ms">' +
                '<span><b>' + it.dev + '</b><small>' + it.time + '</small></span>' +
                '<em>' + money(it.amt) + '</em></div>';
      t += 85;
    });
  });
  $('oTl').innerHTML = html;
}""",
"""/* 年份滚轮每一格的高度 —— 跟 CSS 里 .oyears b 的 44px 对齐 */
const Y_H = 44;
let selYear = null;

function buildOrders(){
  const flat = [];
  ORDERS.forEach(g => g.items.forEach(it => flat.push({ ...it, date:g.date })));
  FLAT.length = 0; flat.forEach((it, i) => FLAT.push({ ...it, i }));

  const sum = Math.round(flat.reduce((a, b) => a + b.amt, 0) * 100) / 100;

  /* 金额：每一位一个滑窗，延迟 0/50/100… 逐位落下 */
  const num = $('oNum'); num.innerHTML = '';
  [...sum.toFixed(2)].forEach((ch, i) => {
    const el = document.createElement('i');
    el.style.setProperty('--dl', (i * 50) + 'ms');
    const u = document.createElement('u');
    u.textContent = ch;
    el.appendChild(u); num.appendChild(el);
  });
  /* 这里的总额是「全部」，不随年份变 */
  $('oSub').textContent = '全部 · ' + ORDERS.length + ' 天 · ' + flat.length + ' 单';

  if(!YEARS.length) return;
  if(selYear === null || !YEARS.some(v => v.y === selYear)) selYear = YEARS[0].y;

  /* 年份滚轮。上下各垫一格，首尾才滚得到正中间。 */
  const yr = $('oYears');
  yr.innerHTML = '<div class="pad"></div>' +
    YEARS.map((v, i) =>
      '<b data-y="' + v.y + '" class="' + (v.y === selYear ? 'on' : '') +
        '" style="--dl:' + (i * 70 + 180) + 'ms">' + v.y +
        '<span class="yr">年</span></b>').join('') +
    '<div class="pad"></div>';

  buildYear();
  requestAnimationFrame(() => {
    yr.scrollTop = Math.max(0, YEARS.findIndex(v => v.y === selYear)) * Y_H;
  });
}

/* 只画选中的那一年 */
function buildYear(){
  const v = YEARS.find(x => x.y === selYear);
  if(!v) return;

  $('oYsum').innerHTML =
    '<span class="y">' + v.y + ' 年</span>' +
    '<span class="days">' + v.days.length + ' 天 · ' + v.items.length + ' 单</span>' +
    '<span class="tot"><u>' + money(v.sum) + '</u></span>';

  /* 首屏 14 行正常排队，其余等滚到视口再播 ——
     一年上百单，全播错时要十几秒，滚到底只会看到一片空白。 */
  const FIRST = 14;
  let t = 60, seen = 0, k = 0, html = '<div class="spine"></div>';
  const late = () => seen < FIRST ? '' : ' later';
  v.days.forEach(g => {
    const dsum = g.items.reduce((a, b) => a + b.amt, 0);
    html += '<div class="odate' + late() + '" style="--dl:' + t + 'ms">' +
              '<i style="--dl:' + t + 'ms"></i>' + g.date +
              '<span class="dsum" style="--dl:' + (t + 80) + 'ms"><u>' + money(dsum) + '</u></span>' +
            '</div>';
    t += 130; seen++;
    g.items.forEach(it => {
      html += '<div class="orow' + late() + '" data-i="' + (k++) + '" style="--dl:' + t + 'ms">' +
                '<span><b>' + it.dev + '</b><small>' + it.time + '</small></span>' +
                '<em>' + money(it.amt) + '</em></div>';
      t += 75; seen++;
    });
  });
  $('oTl').innerHTML = html;
  watchOrderRows();
}

/* 滚到视口的行才补入场动画。只认一遍，免得来回滚动反复闪。 */
let oIo = null;
function watchOrderRows(){
  if(oIo) oIo.disconnect();
  if(!('IntersectionObserver' in window)) return;
  oIo = new IntersectionObserver(es => {
    es.forEach(en => {
      if(!en.isIntersecting) return;
      const el = en.target;
      el.classList.remove('later');
      el.classList.add('slide');
      oIo.unobserve(el);
    });
  }, { root:document.querySelector('.obody'), rootMargin:'0px 0px 90px 0px' });
  document.querySelectorAll('.later').forEach(el => oIo.observe(el));
}

/* 滚轮：滚动停下才切年。滚动中一直切会疯狂重建内容。 */
let yrT = null;
$('oYears').addEventListener('scroll', () => {
  clearTimeout(yrT);
  yrT = setTimeout(() => {
    const yr = $('oYears');
    const i = Math.round(yr.scrollTop / Y_H);
    const v = YEARS[i];
    if(!v || v.y === selYear) return;
    selYear = v.y;
    yr.querySelectorAll('b').forEach(b => b.classList.toggle('on', +b.dataset.y === selYear));

    /* 换年：旧内容快出，新的慢入 */
    const tl = $('oTl'), ys = $('oYsum');
    tl.classList.add('out'); ys.classList.add('out');
    setTimeout(() => {
      tl.classList.remove('out'); ys.classList.remove('out');
      document.querySelector('.obody').scrollTop = 0;
      buildYear();
      playOrders();
    }, 140);
  }, 90);
});

/* 点年份也能跳过去 */
$('oYears').addEventListener('click', e => {
  const b = e.target.closest('b');
  if(!b) return;
  const i = YEARS.findIndex(v => v.y === +b.dataset.y);
  if(i < 0) return;
  $('oYears').scrollTo({ top:i * Y_H, behavior:'smooth' });
});""", '订单页改年份滚轮')

# 长列表的「先显形、滚到才播」两条 CSS
rep("""  @keyframes oPop { from{ transform:scale(0) } }""",
"""  @keyframes oPop { from{ transform:scale(0) } }
  /* 首屏之外的行不排队等动画 */
  #pOrder.play .orow.later,
  #pOrder.play .odate.later{ animation:none; opacity:1; transform:none }
  #pOrder .orow.slide { animation:oRow .46s var(--spring) both }
  #pOrder .odate.slide{ animation:oRow .5s var(--spring) both }
  #pOrder .odate.slide > i{ animation:oPop .44s var(--spring) both }""", '长列表入场')

# 删掉没用的 DLD —— 它只服务于那个被换掉的 7 天柱
rep("""const DLD   = [0,90,40,160,220,130,60];            /* 故意打乱的延迟，递增会像音阶 */

""", "", '删掉没用的 DLD')

# 订单页刚显示出来时滚轮容器才有高度 —— 初始定位要等到那一刻。
# 在设备页就调 buildOrders 的话，容器高是 0，scrollTop 设不上去（定位静默失败）。
rep("""function playOrders(){
  const p = $('pOrder');
  p.classList.remove('play');
  void p.offsetWidth;
  p.classList.add('play');
}""",
"""function playOrders(){
  const p = $('pOrder');
  p.classList.remove('play');
  void p.offsetWidth;
  p.classList.add('play');
  /* 订单页藏看的时候容器高是 0，scrollTop 设不上去。
     等它真显示了再定一次位。 */
  requestAnimationFrame(snapYear);
}

/* 把年份滚轮对齐到当前选中的那一年 */
function snapYear(){
  const yr = $('oYears');
  if(!yr || !YEARS.length || selYear === null) return;
  const i = YEARS.findIndex(v => v.y === selYear);
  if(i >= 0) yr.scrollTop = i * Y_H;
  paintWheel();
}""", '滚轮初始定位')

# ── 修：点滚轮不该进汇总；副标题加个可点的提示 ──
rep("""document.querySelector('.osum').addEventListener('click', openSum);""",
"""/* 整个 .osum 原来都是「进汇总」的点击区。加了年份滚轮之后就不行了 ——
   滚轮也在这个容器里，点年份会连带跳去汇总。所以把滚轮排除掉。 */
document.querySelector('.osum').addEventListener('click', e => {
  if(e.target.closest('.oyears')) return;
  openSum();
});""", '点滚轮不进汇总')

rep("""  .osub{ margin-top:1px; font-size:11.5px; color:#A08E88 }""",
"""  .osub{ margin-top:1px; font-size:11.5px; color:#A08E88;
          display:flex; align-items:center; gap:6px }
  /* 给个能点的暗示 —— 不然谁知道这块可以点进去看汇总 */
  .osub .more{ font-size:10.5px; font-weight:650; color:var(--primary-dim);
               padding:1px 6px; border-radius:7px; background:#F6EBE6 }""", '副标题加汇总提示')

rep("""  $('oSub').textContent = '全部 · ' + ORDERS.length + ' 天 · ' + flat.length + ' 单';""",
"""  $('oSub').innerHTML = '全部 · ' + ORDERS.length + ' 天 · ' + flat.length + ' 单' +
    '<span class="more">看汇总 ›</span>';""", '副标题加提示')

# ── 修：年份滚轮改成「抬手才确认」 ──
# 原来是 scroll 停止后 90ms 就切。用户手指慢慢滑、中间停一下，就提前切了。
# 改成：手指抬起后等吸附动画走完（260ms）再读位置。
rep("""/* 滚轮：滚动停下才切年。滚动中一直切会疯狂重建内容。 */
let yrT = null;
$('oYears').addEventListener('scroll', () => {
  clearTimeout(yrT);
  yrT = setTimeout(() => {
    const yr = $('oYears');
    const i = Math.round(yr.scrollTop / Y_H);
    const v = YEARS[i];
    if(!v || v.y === selYear) return;
    selYear = v.y;
    yr.querySelectorAll('b').forEach(b => b.classList.toggle('on', +b.dataset.y === selYear));

    /* 换年：旧内容快出，新的慢入 */
    const tl = $('oTl'), ys = $('oYsum');
    tl.classList.add('out'); ys.classList.add('out');
    setTimeout(() => {
      tl.classList.remove('out'); ys.classList.remove('out');
      document.querySelector('.obody').scrollTop = 0;
      buildYear();
      playOrders();
    }, 140);
  }, 90);
});""",
"""/* 滚轮：手指抬起才确认。
   原来用 scroll 停止后 90ms 就切 —— 手指慢慢滑、中途停一下，就提前切了。
   现在分开：滚动过程中只把选中的年份标出来（不变内容），
   等抬手、并且吸附动画走完（260ms）才真正换内容。 */
function commitYear(){
  const yr = $('oYears');
  if(!yr || !YEARS.length) return;
  const i = Math.round(yr.scrollTop / Y_H);
  const v = YEARS[i];
  if(!v) return;

  yr.querySelectorAll('b').forEach(b => b.classList.toggle('on', +b.dataset.y === v.y));
  if(v.y === selYear) return;
  selYear = v.y;

  /* 换年：旧内容快出，新的慢入 */
  const tl = $('oTl'), ys = $('oYsum');
  tl.classList.add('out'); ys.classList.add('out');
  setTimeout(() => {
    tl.classList.remove('out'); ys.classList.remove('out');
    document.querySelector('.obody').scrollTop = 0;
    buildYear();
    playOrders();
  }, 140);
}

/* 滚动中只高亮，不动内容 */
$('oYears').addEventListener('scroll', () => {
  const yr = $('oYears');
  const v = YEARS[Math.round(yr.scrollTop / Y_H)];
  if(!v) return;
  yr.querySelectorAll('b').forEach(b => b.classList.toggle('on', +b.dataset.y === v.y));
}, { passive:true });

/* 抬手 → 等吸附走完再确认 */
let yrT = null;
$('oYears').addEventListener('touchend', () => {
  clearTimeout(yrT);
  yrT = setTimeout(commitYear, 260);
}, { passive:true });

/* 鼠标滚轮 / 触控板：停 320ms 当作确认 */
$('oYears').addEventListener('wheel', () => {
  clearTimeout(yrT);
  yrT = setTimeout(commitYear, 320);
}, { passive:true });""", '滚轮改成抬手才确认')

# ── 加：月份折叠（年份下面再分一层）──
rep("""  /* 当年小计 */
  .oysum{ display:flex; align-items:baseline; gap:8px; padding:12px 0 8px; margin-top:10px;
          border-bottom:1px solid var(--outline-var) }""",
"""  /* 当年小计 */
  .oysum{ display:flex; align-items:baseline; gap:8px; padding:12px 0 8px; margin-top:10px;
          border-bottom:1px solid var(--outline-var) }

  /* 月份块。背景保持透明 —— 加底色和描边会让它像一张浮起来的卡，
     而它本来就是同一张流水的一部分。月份之间只用一条细线分开。 */
  .ymon{ border-top:1px solid var(--outline-var) }
  .ymon:first-child{ border-top:0; margin-top:6px }
  /* 月份头。同上，用 grid 保证不换行。 */
  .ymon .mtop{ display:grid; grid-template-columns:auto 1fr auto auto; align-items:center; gap:8px;
               width:100%; height:46px; padding:0; border:0; background:none; cursor:pointer;
               font-family:inherit; color:inherit; text-align:left; line-height:1;
               transition:opacity .2s linear }
  .ymon .mtop > *{ line-height:1; white-space:nowrap }
  .ymon .mtop:active{ opacity:.55 }
  .ymon .mtop .mon{ font-size:13px; font-weight:750; color:var(--primary) }
  .ymon .mtop .days{ font-size:11px; color:#B49A93; font-variant-numeric:tabular-nums }
  .ymon .mtop .tot{ margin-left:auto; font-size:13px; font-weight:750; color:var(--primary);
                    font-variant-numeric:tabular-nums }
  .ymon .mtop .arw{ flex:none; width:15px; height:15px; color:#C4AEA8;
                    transform:rotate(0deg); transition:transform .38s var(--spring) }
  .ymon.open .mtop .arw{ transform:rotate(90deg) }
  /* 折叠用 grid-template-rows 0fr↔1fr，不用 height（不用猜高度） */
  .ymon .mbody{ display:grid; grid-template-rows:0fr;
                transition:grid-template-rows .42s var(--spring) }
  .ymon.open .mbody{ grid-template-rows:1fr }
  .ymon .mbody > .inner{ overflow:hidden; min-height:0 }""", '月份折叠 CSS')

# 展开那一块的内容才播入场
rep("""  #pOrder.play .tl .spine{ animation:oDraw 1s var(--swipe) both }""",
"""  #pOrder.play .tl .spine{ animation:oDraw 1s var(--swipe) both }
  #pOrder.play .ymon{ animation:oRow .5s var(--spring) both; animation-delay:var(--dl) }
  /* 展开那一块的内容才播入场 —— 折着的时候动画白播一遍 */
  #pOrder .ymon.open .odate{ animation:oRow .46s var(--spring) both; animation-delay:var(--dl) }
  #pOrder .ymon.open .odate > i{ animation:oPop .44s var(--spring) both; animation-delay:var(--dl) }
  #pOrder .ymon.open .odate .dsum > u{ animation:oLand .46s var(--spring) both; animation-delay:var(--dl) }
  #pOrder .ymon.open .orow{ animation:oRow .44s var(--spring) both; animation-delay:var(--dl) }
  #pOrder .ymon.open .tl .spine{ animation:oDraw .8s var(--swipe) both }""", '月份折叠动画')

# ── JS：数据层加月份分组 ──
rep("""  YEARS = [...map.entries()]
    .sort((a, b) => b[0] - a[0])                      /* 新的在上面 */
    .map(([y, days]) => {
      const items = [];
      days.forEach(d => d.items.forEach(it => items.push(it)));
      return { y, days, items,
               sum:Math.round(items.reduce((a, b) => a + b.amt, 0) * 100) / 100 };
    });
}""",
"""  YEARS = [...map.entries()]
    .sort((a, b) => b[0] - a[0])                      /* 新的在上面 */
    .map(([y, days]) => {
      const items = [];
      days.forEach(d => d.items.forEach(it => items.push(it)));

      /* 再按月份分一层 —— 一年八个学期月，不分的话一百多行连成一片 */
      const mm = new Map();
      days.forEach(d => {
        if(!mm.has(d.mon)) mm.set(d.mon, []);
        mm.get(d.mon).push(d);
      });
      const months = [...mm.entries()]
        .sort((a, b) => b[0] - a[0])
        .map(([mon, ds]) => {
          const mi = [];
          ds.forEach(d => d.items.forEach(it => mi.push(it)));
          return { mon, days:ds, items:mi,
                   sum:Math.round(mi.reduce((a, b) => a + b.amt, 0) * 100) / 100 };
        });

      return { y, days, items, months,
               sum:Math.round(items.reduce((a, b) => a + b.amt, 0) * 100) / 100 };
    });
}""", '年份再按月份分组')

# ── JS：buildYear 改成渲染月份折叠块 ──
rep("""  /* 首屏 14 行正常排队，其余等滚到视口再播 ——
     一年上百单，全播错时要十几秒，滚到底只会看到一片空白。 */
  const FIRST = 14;
  let t = 60, seen = 0, k = 0, html = '<div class="spine"></div>';
  const late = () => seen < FIRST ? '' : ' later';
  v.days.forEach(g => {
    const dsum = g.items.reduce((a, b) => a + b.amt, 0);
    html += '<div class="odate' + late() + '" style="--dl:' + t + 'ms">' +
              '<i style="--dl:' + t + 'ms"></i>' + g.date +
              '<span class="dsum" style="--dl:' + (t + 80) + 'ms"><u>' + money(dsum) + '</u></span>' +
            '</div>';
    t += 130; seen++;
    g.items.forEach(it => {
      html += '<div class="orow' + late() + '" data-i="' + (k++) + '" style="--dl:' + t + 'ms">' +
                '<span><b>' + it.dev + '</b><small>' + it.time + '</small></span>' +
                '<em>' + money(it.amt) + '</em></div>';
      t += 75; seen++;
    });
  });
  $('oTl').innerHTML = html;
  watchOrderRows();
}

/* 滚到视口的行才补入场动画。只认一遍，免得来回滚动反复闪。 */
let oIo = null;
function watchOrderRows(){
  if(oIo) oIo.disconnect();
  if(!('IntersectionObserver' in window)) return;
  oIo = new IntersectionObserver(es => {
    es.forEach(en => {
      if(!en.isIntersecting) return;
      const el = en.target;
      el.classList.remove('later');
      el.classList.add('slide');
      oIo.unobserve(el);
    });
  }, { root:document.querySelector('.obody'), rootMargin:'0px 0px 90px 0px' });
  document.querySelectorAll('.later').forEach(el => oIo.observe(el));
}""",
"""  /* 换年时重新决定展开哪个月 —— 默认只开最新那个 */
  if(openMonY !== v.y){
    openMon.clear();
    openMonY = v.y;
    if(v.months.length) openMon.add(v.months[0].mon);
  }

  $('oTl').innerHTML = v.months.map(m => {
    const open = openMon.has(m.mon);
    let t = 60, html = '<div class="spine"></div>';
    m.days.forEach(g => {
      const dsum = g.items.reduce((a, b) => a + b.amt, 0);
      html += '<div class="odate" style="--dl:' + t + 'ms">' +
                '<i style="--dl:' + t + 'ms"></i>' + g.date +
                '<span class="dsum" style="--dl:' + (t + 80) + 'ms"><u>' + money(dsum) + '</u></span>' +
              '</div>';
      t += 130;
      g.items.forEach(it => {
        html += '<div class="orow" style="--dl:' + t + 'ms">' +
                  '<span><b>' + it.dev + '</b><small>' + it.time + '</small></span>' +
                  '<em>' + money(it.amt) + '</em></div>';
        t += 75;
      });
    });
    return '<div class="ymon' + (open ? ' open' : '') + '" data-m="' + m.mon + '">' +
             '<button class="mtop" type="button">' +
               '<span class="mon">' + m.mon + ' 月</span>' +
               '<span class="days">' + m.days.length + ' 天 · ' + m.items.length + ' 单</span>' +
               '<span class="tot">' + money(m.sum) + '</span>' +
               '<svg class="arw" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7"/></svg>' +
             '</button>' +
             '<div class="mbody"><div class="inner"><div class="tl">' + html + '</div></div></div>' +
           '</div>';
  }).join('');
}

/* 点月份头：独立开关，不强制手风琴 */
let openMon = new Set();
let openMonY = null;
$('oTl').addEventListener('click', e => {
  const btn = e.target.closest('.mtop');
  if(!btn) return;
  const w = btn.closest('.ymon');
  const mon = +w.dataset.m;
  if(openMon.has(mon)){
    openMon.delete(mon);
    w.classList.remove('open');
  } else {
    openMon.add(mon);
    w.classList.add('open');
  }
});""", 'buildYear 改成月份折叠')
# 长列表的「先显形、滚到才播」两条 CSS —— 后来改成月份折叠了，
# 一次只展开一个月，不需要这套懒渲染。
rep("""  /* 首屏之外的行不排队等动画 */
  #pOrder.play .orow.later,
  #pOrder.play .odate.later{ animation:none; opacity:1; transform:none }
  #pOrder .orow.slide { animation:oRow .46s var(--spring) both }
  #pOrder .odate.slide{ animation:oRow .5s var(--spring) both }
  #pOrder .odate.slide > i{ animation:oPop .44s var(--spring) both }
""", "", '删掉不再用的懒渲染 CSS')

# ── ① 顶部：左边垂直居中，红圈不再空一块 ──
rep("""  .osum{ display:flex; align-items:flex-start; gap:10px; padding-top:2px }""",
"""  /* 左边内容比滚轮矮，顶部对齐的话下方会空一块。居中就不明显了。 */
  .osum{ display:flex; align-items:center; gap:10px; padding-top:2px }""", '顶部改居中')

# ── ② .meta 换行把容器撑高（黄圈）──

# ── ③ 滚轮改回 3 项，并且「小 大 小」──
rep("""  .oyears{ flex:none; width:62px; height:88px; overflow-y:auto;""",
"""  .oyears{ flex:none; width:62px; height:132px; overflow-y:auto;""", '滚轮改回 3 项')

rep("""  .oyears b .yr{ font-size:10.5px; font-weight:600; opacity:.7 }
  .oyears b.on{ color:var(--primary); transform:scale(1.14) }""",
"""  .oyears b .yr{ font-size:10.5px; font-weight:600; opacity:.7 }
  /* 「小 大 小」—— --k 由 JS 按离容器中心的距离实时算（0 最远，1 正中），
     所以滚动过程中大小是连续变化的，不是到了才跳一下。 */
  .oyears b{ transform:scale(calc(.78 + var(--k, 0) * .42));
             color:rgba(143, 76, 56, calc(.28 + var(--k, 0) * .72)) }
  .oyears b.on{ font-weight:750 }""", '滚轮小大小')

# ── JS：滚轮「小 大 小」──
rep("""/* 滚动中只高亮，不动内容 */
$('oYears').addEventListener('scroll', () => {
  const yr = $('oYears');
  const v = YEARS[Math.round(yr.scrollTop / Y_H)];
  if(!v) return;
  yr.querySelectorAll('b').forEach(b => b.classList.toggle('on', +b.dataset.y === v.y));
}, { passive:true });""",
"""/* 「小 大 小」：按每项离容器中心的距离算缩放，滚动中连续变化。
   纯 CSS 做不到这个 —— 只有「选中/未选中」两种状态，中间会跳一下。 */
function paintWheel(){
  const yr = $('oYears');
  if(!yr) return;
  const mid = yr.scrollTop + yr.clientHeight / 2;
  yr.querySelectorAll('b').forEach(b => {
    const bc = b.offsetTop + b.offsetHeight / 2;
    const d = Math.abs(bc - mid) / Y_H;            /* 距离，单位是「几格」 */
    b.style.setProperty('--k', Math.max(0, 1 - d * .62).toFixed(3));
  });
}

/* 滚动中只高亮 + 改缩放，不动内容 */
let wheelRaf = 0;
$('oYears').addEventListener('scroll', () => {
  const yr = $('oYears');
  const v = YEARS[Math.round(yr.scrollTop / Y_H)];
  if(v) yr.querySelectorAll('b').forEach(b => b.classList.toggle('on', +b.dataset.y === v.y));
  /* rAF 节流 —— scroll 一帧可能触发好几次 */
  if(wheelRaf) return;
  wheelRaf = requestAnimationFrame(() => { wheelRaf = 0; paintWheel(); });
}, { passive:true });""", '滚轮小大小')

# 初始化时画一次
rep("""  buildYear();
  requestAnimationFrame(() => {
    yr.scrollTop = Math.max(0, YEARS.findIndex(v => v.y === selYear)) * Y_H;
  });
}""",
"""  buildYear();
  requestAnimationFrame(() => {
    yr.scrollTop = Math.max(0, YEARS.findIndex(v => v.y === selYear)) * Y_H;
    paintWheel();
  });
}""", '滚轮初始画一次')

# ── JS：换年时不重播总额 ──
rep("""  /* 换年：旧内容快出，新的慢入 */
  const tl = $('oTl'), ys = $('oYsum');
  tl.classList.add('out'); ys.classList.add('out');
  setTimeout(() => {
    tl.classList.remove('out'); ys.classList.remove('out');
    document.querySelector('.obody').scrollTop = 0;
    buildYear();
    playOrders();
  }, 140);
}""",
"""  /* 换年：旧内容快出，新的慢入。
     注意这里不能调 playOrders() —— 它会重播整个 #pOrder.play 的动画，
     连顶部的总额也重新逐位落下。总额根本没变，闪一下反而像出错了。 */
  const tl = $('oTl'), ys = $('oYsum');
  tl.classList.add('out'); ys.classList.add('out');
  setTimeout(() => {
    tl.classList.remove('out'); ys.classList.remove('out');
    document.querySelector('.obody').scrollTop = 0;
    buildYear();
    replayYear();
  }, 140);
}

/* 只重播当年那一块的入场，不碰顶部总额 */
function replayYear(){
  $('oTl').querySelectorAll('.ymon, .odate, .orow, .mtop, .arw').forEach(el => {
    el.style.animation = 'none';
    void el.offsetWidth;
    el.style.animation = '';
  });
}""", '换年不重播总额')

rep("""  /* 当年小计 */
  .oysum{ display:flex; align-items:baseline; gap:8px; padding:12px 0 8px; margin-top:10px;
          border-bottom:1px solid var(--outline-var) }""",
"""  /* 当年小计。定高 + 行高 1 —— 不依赖子元素的行高。
     baseline 对齐时 11px 的小字会膨胀到 29px，把容器撑高一倍。 */
  /* 当年小计。用 grid 不用 flex —— flex 下这个 11px 的小字总会被挤到第二行，
     行高翻倍把容器从 46px 撑到 80px。grid 的列是定死的，不会。 */
  .oysum{ display:grid; grid-template-columns:auto 1fr auto; align-items:center; gap:8px;
          height:46px; margin-top:10px;
          border-bottom:1px solid var(--outline-var); line-height:1 }
  .oysum > *{ line-height:1; white-space:nowrap }""", 'oysum 定高')


# 滚轮缩放改用 rect 算 —— offsetTop 不可靠（.oyears 不是定位祖先）
rep("""function paintWheel(){
  const yr = $('oYears');
  if(!yr) return;
  const mid = yr.scrollTop + yr.clientHeight / 2;
  yr.querySelectorAll('b').forEach(b => {
    const bc = b.offsetTop + b.offsetHeight / 2;
    const d = Math.abs(bc - mid) / Y_H;            /* 距离，单位是「几格」 */
    b.style.setProperty('--k', Math.max(0, 1 - d * .62).toFixed(3));
  });
}""",
"""function paintWheel(){
  const yr = $('oYears');
  if(!yr) return;
  /* 用 rect 算，不用 offsetTop —— .oyears 不是定位祖先，offsetTop 会算到别处去 */
  const r = yr.getBoundingClientRect();
  const mid = r.top + r.height / 2;
  yr.querySelectorAll('b').forEach(b => {
    const br = b.getBoundingClientRect();
    const d = Math.abs(br.top + br.height / 2 - mid) / Y_H;   /* 距离，单位是「几格」 */
    b.style.setProperty('--k', Math.max(0, 1 - d * .62).toFixed(3));
  });
}""", '滚轮缩放用 rect')

# 清掉死代码 FLAT —— 只在 buildOrders 里被填，没人读
rep("""const FLAT = [];
const money = n => '&yen;' + n.toFixed(2);""",
"""const money = n => '&yen;' + n.toFixed(2);""", '删掉死代码 FLAT')

rep("""  const flat = [];
  ORDERS.forEach(g => g.items.forEach(it => flat.push({ ...it, date:g.date })));
  FLAT.length = 0; flat.forEach((it, i) => FLAT.push({ ...it, i }));
""",
"""  const flat = [];
  ORDERS.forEach(g => g.items.forEach(it => flat.push({ ...it, date:g.date })));
""", '删掉 FLAT 的填充')

rep("""function loadOrders(force){
  /* 演示模式下不去问真实订单 —— 和 loadDevices 同一个道理。 */
  if(demoMode) return;
  Android.orders(!!force);   /* 结果从 window.onOrders 回来 */
}""",
"""function loadOrders(force){
  /* 演示模式下不去问真实订单 —— 和 loadDevices 同一个道理。 */
  if(demoMode) return;
  /* 手上还没数据就先转圈 —— 空白页什么也不说，人会以为坏了。
     有缓存的话原生会立刻把缓存丢过来，转圈一闪就过。 */
  if(!ORDERS.length) $('pOrder').classList.add('loading');
  Android.orders(!!force);   /* 结果从 window.onOrders 回来 */
}""", '订单页加载中提示')

rep("""window.onOrders = raw => {
  if(demoMode) return;       /* 双保险 */
  setOrders(JSON.parse(raw) || []);""",
"""window.onOrders = raw => {
  if(demoMode) return;       /* 双保险 */
  $('pOrder').classList.remove('loading');
  setOrders(JSON.parse(raw) || []);""", '收到订单就去掉转圈')

rep("""window.onHasToken = v => { if(v === '1') showDevList(); };""",
"""window.onHasToken = v => {
  /* '?' = 网络没通，但 token 还在。这时也得进列表页 ——
     把人停在登录页，他会以为掉登录了，跑去重新发验证码，
     而每日短信额度就那么多。 */
  if(v === '1' || v === '?') showDevList();
};""", 'hasToken 网络失败也进列表页')

rep("""      <div class="oempty">""",
"""      <div class="oload"><span class="spin"></span>正在拉取账单…</div>

      <div class="oempty">""", '加载提示的 HTML')

rep("""  .obody{ flex:1; overflow-y:auto; padding:0 18px 16px }""",
"""  .obody{ flex:1; overflow-y:auto; padding:0 18px 16px }

  /* 拉账单时的转圈 —— 只在手上一份数据都没有时出现 */
  .oload{ display:none; flex-direction:column; align-items:center; gap:12px;
          padding:70px 0; color:#B49A93; font-size:13px }
  #pOrder.loading .oload{ display:flex }
  #pOrder.loading .obody{ display:none }
  .spin{ width:22px; height:22px; border:2.5px solid var(--outline-var);
         border-top-color:var(--primary); border-radius:50%;
         animation:spin .8s linear infinite }
  @keyframes spin{ to{ transform:rotate(360deg) } }""", '加载提示的 CSS')

rep("""window.onOrders = raw => {
  if(demoMode) return;       /* 双保险 */
  $('pOrder').classList.remove('loading');
  setOrders(JSON.parse(raw) || []);""",
"""window.onOrders = raw => {
  if(demoMode) return;       /* 双保险 */
  $('pOrder').classList.remove('loading');
  /* 原生那边已经校验过缓存是合法 JSON 数组了，这里再兜一层：
     万一哪天接口返回的形状变了，也不该把整个订单页炸成空白。 */
  let list = [];
  try { const v = JSON.parse(raw); if(Array.isArray(v)) list = v; }
  catch(e){ list = []; }
  setOrders(list);""", 'onOrders 兜一层解析保护')

os.makedirs(os.path.dirname(DST), exist_ok=True)
io.open(DST, 'w', encoding='utf-8').write(s)
print('生成完毕 →', DST, len(s), 'bytes')
print('—— 该有的 ——')
for k in ['Android.askCamera', 'window.onCamReady', 'Android.scanFrame',
          'Android.addDeviceFromCode', 'Android.addDeviceByNo', 'window.onBack',
          'id="cam"', 'id="bManual"', 'id="iSn"', 'loadDevices', 'setOrders']:
    print(('  ok  ' if k in s else '  缺! ') + k)
print('—— 不该有的 ——')
for k in ['const DEV = [', 'const ORDERS = [', 'value="13800001234"',
          '校园直饮水', '这只是一个原型', 'Android.scanQR', 'zxing', '1.6 秒']:
    print(('  残留! ' if k in s else '  ok 已清  ') + k)
