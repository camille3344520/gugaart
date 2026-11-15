/* 播放器：控件 / 暂停 / 六个功能的页边面板（画面让到一边）/ 快进快退 / 跳过片头（玻璃悬浮钮）/ 片尾下一集 / 缓冲 / 出错 */
(function () {
'use strict';
const APP = window.APP, U = window.U, L = window.LIB, { ic, im, esc, pad } = APP, F = APP.F;
const O = U.det('颜心记'), EPS = L.series['颜心记'].episodes, EP = EPS[4], NEXT = EPS[5], TOTAL = 2701, POS = 1590;
const heat = (n) => { const o = []; for (let i = 0; i < n; i++) { const x = i / n; const v = Math.abs(Math.sin(i * 0.37) + 0.6 * Math.sin(i * 0.11 + 1) + 0.35 * Math.sin(i * 1.3 + 2)) / 1.95; o.push(0.14 + 0.86 * v * (0.55 + 0.45 * Math.sin(x * 3.1))); } return o; };
// 字幕、弹幕的文字是编的（服务器给不了）
const SUBS = ['', '南霁风：你到底是什么人？', '南霽風：你到底是什麼人？', 'Nan Jifeng: Who are you, really?'];
const DM = [['这段打光绝了', 0, 8], ['前方高能', 1, 30], ['江大人又在装冷静', 0, 52], ['二刷来了', 2, 18], ['组队成功！', 1, 66], ['注意他的眼神', 0, 40], ['脸盲设定好评', 2, 78]];
const FUNCS = [
  ['eps', '选集', 'EPISODE', EPS.slice(0, 12).map((e) => `第 ${e.e} 集　${e.name}`), 4],
  ['sub', '字幕', 'SUBTITLES', ['关', '简体中文 · 内封', '繁體中文 · 内封', 'English · 外挂'], 1],
  ['audio', '音轨', 'AUDIO', ['国语 · AAC 立体声', '国语 · AC3 5.1'], 0],
  ['speed', '倍速', 'SPEED', ['0.75×', '1.0×', '1.25×', '1.5×', '2.0×', '3.0×'], 1],
  ['quality', '画质', 'QUALITY', ['原画 · 1080p 直连', '1080p · 8 Mbps 转码', '720p · 4 Mbps 转码'], 0],
  ['dm', '弹幕', 'DANMAKU', ['关', '稀疏', '适中', '密集'], 2],
];
const short = (k, v) => k === 'eps' ? v.split('　')[0] : v.split(' · ')[0];
const tl = (p, o) => { o = o || {}; const h = heat(110);
  return `<div class="tl${o.f0 ? ' ' : ''}" ${o.focus ? 'data-f' : ''} ${o.f0 ? 'data-f0' : ''} style="--p:${p}"><span class="t num tcur">${U.clock(p * TOTAL)}</span><div class="trk"><div class="heat">${h.map((v, i) => `<i class="${i / h.length < p ? 'on' : ''}" style="height:${Math.round(4 + v * 30)}px"></i>`).join('')}</div>
    <div class="ln"><u></u>${o.buf ? `<s style="width:${o.buf * 100}%"></s>` : ''}<i class="seg" style="left:0;width:3.4%"><b>片头</b></i><i class="seg" style="left:93.6%;width:6.4%"><b>片尾</b></i><em class="hd"></em>${o.ghost != null ? `<em class="hd gh" style="left:${o.ghost * 100}%"></em>` : ''}</div></div><span class="t num">${U.clock(TOTAL)}</span></div>`; };
const top = (st) => `<div class="ptop"><div class="l">${U.logo(O, 'sm')}<div><b>第 ${EP.e} 集　${esc(EP.name)}</b><span>第 1 季 · 共 40 集 · 原画 1080p</span></div></div>
  <div class="r"><div class="seat">${st === 'pause' ? APP.msc(56) + `<div><b>暂停</b><span>停在 ${U.clock(POS)}</span></div>` : ''}</div><div class="ck"><em class="num">21:47</em><span>预计 22:06 播完</span></div></div></div>`;
const ibtn = (icn, label, o) => `<a class="pb${o && o.big ? ' big' : ''}" data-f ${o && o.attr || ''} title="${label}">${ic(icn)}<small>${label}</small></a>`;
const osd = (st, sel) => `<div class="osd">${tl(POS / TOTAL, { focus: 1, f0: st === 'bar' })}
  <div class="brow"><div class="tr" data-fg>${ibtn('prev', '上一集')}${ibtn('rew', '退 10 秒')}${ibtn(st === 'pause' ? 'play' : 'pause', st === 'pause' ? '继续' : '暂停', { big: 1, attr: (st === '' || st === 'pause' ? 'data-f0 ' : '') + 'data-act="play"' })}${ibtn('fwd', '进 10 秒')}${ibtn('next', '下一集')}</div>
  <div class="fn" data-fg>${FUNCS.map((f) => `<a class="fb" data-f ${st === 'btns' && f[0] === 'sub' ? 'data-f0' : ''} data-go="p-${f[0]}"><b>${f[1]}</b><small>${short(f[0], f[3][sel[f[0]]])}</small></a>`).join('')}<a class="pb fav" data-f data-act="fav">${ic('heart')}<small>收藏</small></a></div></div></div>`;
const video = (st, sel) => `<div class="vid">${im(U.I(O, 'backdrop'), 'frame')}<div class="dmk d${sel.dm}">${DM.map((d) => `<span class="c${d[1]}" style="top:${d[1] * 46 + 26}px;left:${d[2]}%">${d[0]}</span>`).join('')}</div><div class="subl" style="${sel.sub ? '' : 'display:none'}">${SUBS[sel.sub]}</div></div>`;
const panel = (open, sel) => `<aside class="ppn scope open"><div class="ppn-h"><em>播放设置 · SETTINGS</em><span>上下换功能　左右改值</span></div><div data-fg>${FUNCS.map((f) => { const on = f[0] === open, i = sel[f[0]], n = f[3].length;
  return `<a class="pr${on ? ' ex' : ''}" data-f ${on ? 'data-f0' : ''} data-fn="${f[0]}"><b>${f[1]}</b><i class="en">${f[2]}</i><span class="val"><u class="ar${i > 0 ? '' : ' off'}">‹</u><span class="v">${esc(f[0] === 'eps' ? f[3][i].split('　')[0] : f[3][i])}</span><u class="ar${i < n - 1 ? '' : ' off'}">›</u></span>
    <span class="more">${f[0] === 'eps' ? `<span class="epn">${esc(f[3][i].split('　')[1])}</span>` : ''}<span class="dots">${f[3].map((_, k) => `<i class="${k === i ? 'on' : ''}"></i>`).join('')}</span><span class="cnt num">${i + 1} / ${n}</span></span></a>`; }).join('')}</div>
  <p class="ppn-n">返回键收起，回到全幅。</p></aside>`;
const DEF = () => { const s = {}; FUNCS.forEach((f) => { s[f[0]] = f[4]; }); return s; };
APP.SCREENS.player = {
  title: '播放器', rail: false, note: '播放时画面上没有常驻的东西，她也不出现；暂停才坐进顶栏右边留给她的一格。按下打开页边面板：画面缩到左边，右边空出的页边一栏排六个功能，上下换功能、左右直接改值，画面和字幕一个像素都不挡。只有「跳过片头」是液态玻璃（固定悬浮按钮）。',
  path: '<b>方向键路径</b>　看片时：左右 = 退 / 进 10 秒（长按逐级加速到 ×3，带预览图），确定 = 暂停并出控件，上 = 出控件（焦点在进度条），下 = 打开页边面板。控件：第一行进度条（左右拖），第二行左起 上一集 / 退 10 / 播放 / 进 10 / 下一集，再往右是 选集 字幕 音轨 倍速 画质 弹幕 收藏；默认焦点「播放」。页边面板：上下换功能，左右改值，返回收起。「跳过片头」「下一集」一出现就拿到焦点，确定执行，返回或任何方向键放弃。',
  states: [['', '控件 · 焦点在播放'], ['bar', '焦点在进度条'], ['btns', '焦点在功能键'], ['pause', '暂停 · 她坐进顶栏'], ...FUNCS.map((f) => ['p-' + f[0], '面板 · ' + f[1]]), ['seek', '快进（长按加速 + 预览）'], ['skip', '跳过片头（玻璃）'], ['next', '片尾 · 下一集'], ['buffer', '缓冲中'], ['error', '播放出错']],
  render(st) {
    const sel = this.sel = this.sel || DEF(), open = st.startsWith('p-') ? st.slice(2) : null;
    if (st === 'error') return `<div class="pl err"><div class="vid black"></div><div class="errbox"><div class="extra2"><b>号外</b><span>这一集没能放出来</span></div><p>解码器打不开这条视频流（HEVC 10 bit）。已经自动试过 ExoPlayer 和 VLC，都不行。</p><dl><dt>片子</dt><dd>颜心记 · 第 ${EP.e} 集</dd><dt>线路</dt><dd>直连</dd><dt>错误</dt><dd class="num">ERROR_CODE_DECODING_FORMAT_UNSUPPORTED</dd></dl>
      <div class="cta" data-fg>${U.btn('换 mpv 内核再试', { ic: 'replay', cls: 'pri', attr: 'data-f0' })}${U.btn('请服务器转码', { ic: 'hd' })}${U.btn('返回详情', { ic: 'back' })}</div></div></div>`;
    if (st === 'next') return `<div class="pl nx"><div class="nx-bd">${im(U.I(O, 'backdrop'))}<div class="sh1"></div><div class="sh2"></div></div>
      <div class="nx-win">${im(U.I(O, 'backdrop'))}<span>片尾 · 还在播　${U.clock(TOTAL - 96)} / ${U.clock(TOTAL)}</span></div>
      <section class="nx-l"><div class="kick">下一集 · NEXT　第 ${NEXT.e} 集 · ${Math.round(NEXT.runtimeSec / 60)} 分钟</div><h1>${esc(NEXT.name)}</h1><p class="lead l2">${esc(U.cut(NEXT.overview, 96))}</p>
        <div class="count"><i class="bar"><u></u></i><span><b class="num">8</b> 秒后自动播放</span></div>
        <div class="cta" data-fg>${U.btn('立即播放', { ic: 'play', cls: 'pri', attr: 'data-f0' })}${U.btn('看完片尾', {})}${U.btn('返回详情', { ic: 'back' })}</div>
        <div class="nx-n">${U.pose({ wl: 60, wr: 60, eyes: '^', mouth: 'D' }, '坐着鼓掌（H15）', `第 ${EP.e} 集看完了，校对勾我替你画上了。`)}${U.tick()}</div></section>
      <div class="nx-still">${im(NEXT.thumb)}<span>第 ${NEXT.e} 集 剧照</span></div></div>`;
    const cls = open ? 'open' : st === 'pause' ? 'ctl paused' : (st === '' || st === 'bar' || st === 'btns') ? 'ctl' : st;
    let extra = '';
    if (st === 'seek') extra = `<div class="osd sk">${tl((POS + 280) / TOTAL, { ghost: POS / TOTAL })}<div class="skinfo"><b class="num">+ 4:40</b><span>长按中 · ×3</span><span>松开就从这里播</span></div><div class="pvw" style="left:calc(232px + (100% - 464px) * ${(POS + 280) / TOTAL})">${im(EPS[6].thumb)}<b class="num">${U.clock(POS + 280)}</b></div></div>`;
    if (st === 'buffer') extra = `<div class="buf"><b class="num">38%</b><span>正在翻到下一页……</span><small>4.2 MB/s · 直连</small><i class="bar"><u style="width:38%"></u></i></div><div class="osd sk">${tl(POS / TOTAL, { buf: 0.64 })}</div>`;
    if (st === 'skip') extra = `<div class="skn"><span>片头还剩 1:12</span></div>`;
    const html = `<div class="pl ${cls}">${video(st, sel)}${open || st === 'skip' ? '' : top(st)}${cls.startsWith('ctl') ? osd(st, sel) : ''}${extra}${open ? `<div class="osd op">${tl(POS / TOTAL)}</div>` + panel(open, sel) : ''}</div>`;
    return st === 'skip' ? { content: html, chrome: `<div class="g skp" data-mat="bar" style="left:1456px;top:884px;width:368px;height:100px"><a class="skb" data-f data-f0>${ic('next')}<b>跳过片头</b><small>确定</small></a></div>` } : html;
  },
  init(root, st) { root.style.setProperty('--spot', APP.spotOf(U.I(O, 'poster'))); },
  apply(root) { const s = this.sel, sub = root.querySelector('.subl'), d = root.querySelector('.dmk'); sub.textContent = SUBS[s.sub]; sub.style.display = s.sub ? '' : 'none'; d.className = 'dmk d' + s.dm; },
  key(root, k) {
    const c = F.cur, pl = root.querySelector('.pl'); if (!c) return;
    if (c.classList.contains('pr') && (k === 'left' || k === 'right')) { const f = FUNCS.find((x) => x[0] === c.dataset.fn), n = f[3].length, i = this.sel[f[0]] = APP.clamp(this.sel[f[0]] + (k === 'right' ? 1 : -1), 0, n - 1);
      c.querySelector('.v').textContent = f[0] === 'eps' ? f[3][i].split('　')[0] : f[3][i]; const ar = c.querySelectorAll('.ar'); ar[0].classList.toggle('off', i === 0); ar[1].classList.toggle('off', i === n - 1);
      c.querySelectorAll('.dots i').forEach((x, j) => x.classList.toggle('on', j === i)); c.querySelector('.cnt').textContent = `${i + 1} / ${n}`; const en = c.querySelector('.epn'); if (en) en.textContent = f[3][i].split('　')[1]; this.apply(root); return true; }
    if (c.classList.contains('tl') && (k === 'left' || k === 'right')) { const p = APP.clamp(+c.style.getPropertyValue('--p') + (k === 'right' ? 10 : -10) / TOTAL, 0, 1); c.style.setProperty('--p', p); c.querySelector('.tcur').textContent = U.clock(p * TOTAL); c.querySelectorAll('.heat i').forEach((h, i, a) => h.classList.toggle('on', i / a.length < p)); return true; }
    if (c.classList.contains('skb') && k !== 'ok' && k !== 'menu') { APP.go(''); return true; }
    if (k === 'down' && pl.classList.contains('ctl') && (c.closest('.brow'))) { APP.go('p-sub'); return true; }
  },
  onFocus(root, el) { root.querySelectorAll('.pr').forEach((r) => r.classList.toggle('ex', r === el)); },
  ok(root, c) { if (c.dataset.act === 'play') return APP.go(APP.st === 'pause' ? '' : 'pause'); if (c.dataset.act === 'fav') { c.classList.toggle('faved'); c.querySelector('use').setAttribute('href', c.classList.contains('faved') ? '#i-heartf' : '#i-heart'); return; } if (c.classList.contains('skb')) return APP.go(''); APP.toast('已执行（演示）'); },
};
})();
