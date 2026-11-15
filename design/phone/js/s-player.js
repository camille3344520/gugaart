/* 14 播放器（横屏 905 x 412 = 3168 x 1440）：调设置时画面让到左边，右侧页边排选项，画面和字幕一个像素都不挡。播放时没有她；暂停、片尾、出错时她才出现在留给她的格子里。 */
(function () {
'use strict';
const { ic, im, pad, esc, D, clamp } = APP, DB = window.DB;
const T = D['颜心记'], EPS = DB.series['颜心记'].episodes, CUR = 4, TOTAL = 2701, POS = 1590;
const heat = APP.heat(110);
// 弹幕内容、字幕文本、音轨 / 画质选项是编的（服务器给不了）
const DM = [['这场戏的灯光绝了', 0], ['前方高能', 1], ['江大人又在装冷静', 0], ['脸盲还这么会认人', 2], ['组队成功！', 0], ['南将军：我只是路过', 1], ['二刷来了', 0], ['烛光比剧情还稳', 2], ['注意右边那位的眼神', 0]];
const SUBS = ['', '江大人，这一趟，我跟你去。', '江大人，這一趟，我跟你去。', 'My lord, I am coming with you this time.'];
const P = {
  eps: { t: '选集', en: 'EPISODES · 第 1 季 · 40 集' },
  sub: { t: '字幕', en: 'SUBTITLES', opts: ['关', '简体中文 · 内封', '繁體中文 · 内封', 'English · 外挂'], steps: [['size', '字号', ['小', '中', '大', '特大']], ['delay', '延迟', null]], more: '在线找字幕' },
  audio: { t: '音轨', en: 'AUDIO', opts: ['国语 AAC 2.0', '国语 AC3 5.1', '粤语 AAC 2.0'], steps: [['sync', '音画同步', null]] },
  speed: { t: '倍速', en: 'SPEED', opts: ['0.5×', '0.75×', '1.0×', '1.25×', '1.5×', '2.0×', '3.0×'], steps: [], note: '长按画面 = 临时 2.0×' },
  quality: { t: '画质', en: 'QUALITY · EDITION', opts: ['原画 4K HDR · 直连', '1080p 8 Mbps · 转码', '720p 4 Mbps · 转码'], steps: [], note: '版本：4K HDR10 · 2.61 GB', more: '换一个版本（共 2 个）' },
  dm: { t: '弹幕', en: 'DANMAKU', opts: ['关', '稀疏', '适中', '密集'], steps: [['alpha', '不透明', ['40%', '60%', '80%', '100%']], ['dsize', '字号', ['小', '中', '大']], ['area', '区域', ['¼ 屏', '半屏', '¾ 屏', '全屏']]], more: '弹幕源：自动匹配 · 2,140 条' },
};
const short = { eps: () => `第 ${EPS[CUR].e} 集`, sub: (S) => P.sub.opts[S.sel.sub].split(' · ')[0], audio: (S) => P.audio.opts[S.sel.audio], speed: (S) => P.speed.opts[S.sel.speed], quality: (S) => P.quality.opts[S.sel.quality].split(' ')[1] || '原画', dm: (S) => P.dm.opts[S.sel.dm] };
const panel = (k, S, stepText) => { const M = P[k];
  const body = k === 'eps' ? EPS.slice(1, 9).map((e, i) => { const n = i + 1; return `<a data-ep="${n}" class="${S.ep === n ? 'on' : ''}"><em class="num">${e.e}</em><span>${esc(e.name)}</span><small>${S.ep === n ? '在播' : e.played ? '已看' : ''}</small></a>`; }).join('')
    : M.opts.map((o, i) => `<a data-opt="${i}" class="${S.sel[k] === i ? 'on' : ''}"><i></i>${o}</a>`).join('');
  return `<div class="col k-${k}"><div class="ck">${M.en}</div><h4>${M.t}</h4><div class="copts">${body}</div>` +
    (M.steps || []).map((s) => `<div class="cst"><em>${s[1]}</em><a data-step="${s[0]}:-1">${ic('min')}</a><b class="num">${stepText(s)}</b><a data-step="${s[0]}:1">${ic('plus')}</a></div>`).join('') +
    (M.note ? `<p class="cnote">${M.note}</p>` : '') + (M.more ? `<a class="cmore">${M.more} ${ic('chevr')}</a>` : '') +
    `<a class="cclose" data-act="close">收起，回到全幅 ${ic('arrow')}</a></div>`; };
const nextCol = () => { const e = EPS[CUR + 1]; return `<div class="col k-next"><div class="ck">NEXT · 片尾</div><h4>下一集</h4><div class="nx">${K.thumb(e.thumb, '颜心记')}<em class="num">第 ${e.e} 集</em><b>${esc(e.name)}</b><span>${APP.dur(e.runtimeSec)}</span></div><div class="cd"><i><u></u></i><span><b class="num">8</b> 秒后自动播放</span></div><a class="b-btn" data-act="close">${ic('play')}立即播放</a><a class="cclose" data-act="close">留在片尾，看完字幕 ${ic('arrow')}</a></div>`; };

APP.reg('player', '播放器', {
  title: '播放器', land: 1, note: '横屏 905 × 412。点「字幕」「选集」等，画面让到左边约 75 %，设置排进右侧页边；跳过片头、片尾下一集、手势提示都贴边，不压字幕。播放时没有她；暂停时她坐进顶栏的一格，看完一集、放不出来时在留给她的位置。',
  states: [['ctl', '控件显示'], ['idle', '只有画面'], ['pause', '暂停'], ['eps', '选集'], ['sub', '字幕'], ['audio', '音轨'], ['speed', '倍速'], ['quality', '画质 / 版本'], ['dm', '弹幕设置'], ['skip', '跳过片头'], ['next', '片尾 · 下一集'], ['ended', '看完一集'], ['buffer', '缓冲中'], ['error', '播放出错'], ['lock', '锁屏'], ['bright', '亮度手势'], ['vol', '音量手势'], ['seek', '快进手势'], ['long', '长按 2×']],
  build: () => ({ content: `
<div class="stage">${im(T.images.backdrop, 'frame')}<div class="dmk">${DM.map((d, i) => `<span class="c${d[1]}" style="top:${(i % 3) * 30 + 8}px;animation-delay:${-(Math.floor(i / 3) * 4.7 + (i % 3) * 1.9 + 0.8).toFixed(1)}s">${d[0]}</span>`).join('')}</div><div class="subline"></div><div class="sh-t"></div><div class="sh-b"></div><div class="veil"></div></div>
<div class="ctl top"><a data-back>${ic('back')}</a>${im(T.images.logo, 'plogo')}<div class="ept"><b>第 ${EPS[CUR].e} 集　${esc(EPS[CUR].name)}</b><span>4K HDR10 · 国语 AAC 2.0 · 直连</span></div>
  <div class="pm">${APP.msc('round', 38)}<div><b>暂停</b>停在 <span class="tcur num"></span></div></div><a data-act="fav">${ic('heart')}</a><a data-act="lock">${ic('unlock')}</a></div>
<div class="ctl mid"><a>${ic('rew')}</a><a class="big" data-act="play">${ic('pause')}</a><a>${ic('fwd')}</a></div>
<div class="ctl bot">
  <div class="trow"><span class="tcur num"></span><div class="tl"><div class="heat">${heat.map((v) => `<i style="--d:${(1.5 + v * 7.5).toFixed(1)}px"></i>`).join('')}</div><div class="track"><i></i></div><div class="ticks">${'<i></i>'.repeat(10)}</div><u class="mkr" style="left:3.3%" title="片头"></u><u class="mkr" style="left:93%"></u><i class="head"></i></div><span class="num">${APP.clock(TOTAL)}</span></div>
  <div class="brow">${Object.keys(P).map((k) => `<a data-p="${k}">${P[k].t}<b data-val="${k}"></b></a>`).join('')}</div>
</div>
<a class="p-skip" data-act="skip">跳过片头 ${ic('skip')}<span class="num">到 1:28</span></a>
<div class="p-lock"><a data-act="lock">${ic('lock')}</a><span>已锁定 · 点锁解开</span></div>
<div class="p-g bright"><i>${ic('sun')}</i><u><s style="height:62%"></s></u><b class="num">62%</b><span>亮度</span></div>
<div class="p-g vol"><i>${ic('vol')}</i><u><s style="height:35%"></s></u><b class="num">35%</b><span>音量</span></div>
<div class="p-seek"><em>快进</em><b class="num">+ 0:42</b><span class="num">27:12 / ${APP.clock(TOTAL)}</span><i><u style="width:60.4%"></u></i></div>
<div class="p-long">${ic('skip')}<b class="num">2.0×</b><span>松手恢复</span></div>
<div class="p-buf"><i><u></u></i><b>缓冲中</b><span class="num">3.2 MB/s · 已缓冲到 27:40</span></div>
<div class="p-err"><div class="pe">${K.poseArt('err')}<small>轮廓占位 · ${K.POSES.err.cap}</small></div><div><div class="ck">ERROR · 第 ${EPS[CUR].e} 集</div><h4>这一集放不出来</h4><p>ExoPlayer、VLC、mpv 都试过了：源文件打不开（HTTP 404）。可以换个版本，或者请服务器转码。</p><div class="eb"><a class="b-btn">${ic('replay')}再试一次</a><a class="b-link">换版本</a><a class="b-link">服务器转码</a><a class="b-link dim">复制错误信息</a></div></div></div>
<div class="p-end">${im(T.images.backdrop, 'bg')}<i class="sh"></i><a class="x" data-back>${ic('back')}</a>
  <div class="el"><div class="ck">第 ${EPS[CUR].e} 集 · 看完了</div><h4>本集完</h4>${im(T.images.logo, 'elg')}<div class="en">${APP.msc('round', 44, 'data-say=".p-end .say"')}<p class="say">这一集记进读者证了。下一集，大婚。</p></div></div>
  <div class="er"><div class="ck">NEXT</div>${K.thumb(EPS[CUR + 1].thumb, '颜心记')}<em class="num">第 ${EPS[CUR + 1].e} 集</em><b>${esc(EPS[CUR + 1].name)}</b><a class="b-btn">${ic('play')}播放下一集</a><div class="e2"><a class="b-link">${ic('replay')}重看这一集</a><a class="b-link" data-act="fav2">${ic('heart')}收藏</a><a class="b-link">回详情</a></div></div></div>
<div class="panel-host"></div>` }),
  init(root) {
    const S = root.__S = { sel: { sub: 1, audio: 0, speed: 2, quality: 0, dm: 2 }, v: { size: 1, delay: 0, sync: 0, alpha: 2, dsize: 1, area: 1 }, open: null, p: POS / TOTAL, ep: CUR };
    const host = root.querySelector('.panel-host'), sub = root.querySelector('.subline'), dmk = root.querySelector('.dmk');
    const stepText = (st) => st[2] ? st[2][S.v[st[0]]] : ((S.v[st[0]] > 0 ? '+' : S.v[st[0]] < 0 ? '−' : '') + Math.abs(S.v[st[0]] / 10).toFixed(1) + ' 秒');
    const draw = () => {
      root.classList.toggle('open', !!S.open); host.classList.toggle('fresh', S.open !== S.last); S.last = S.open;
      host.innerHTML = S.open === 'next' ? nextCol() : S.open ? panel(S.open, S, stepText) : ''; APP.fill(host);
      sub.textContent = SUBS[S.sel.sub]; sub.style.display = S.sel.sub ? '' : 'none'; sub.style.fontSize = [15, 18, 22, 26][S.v.size] + 'px';
      dmk.style.display = S.sel.dm ? '' : 'none'; dmk.style.opacity = [0.4, 0.6, 0.8, 1][S.v.alpha]; dmk.style.fontSize = [13, 15, 18][S.v.dsize] + 'px'; dmk.dataset.den = S.sel.dm;
      root.querySelectorAll('[data-val]').forEach((el) => { el.textContent = short[el.dataset.val](S); });
      root.querySelectorAll('[data-p]').forEach((el) => el.classList.toggle('on', el.dataset.p === S.open));
    };
    const pause = (on) => { root.classList.toggle('paused', on); root.querySelectorAll('[data-act="play"] use').forEach((u) => u.setAttribute('href', on ? '#i-play' : '#i-pause')); const m = APP.mascotOf(root, '.pm .msc'); if (m && on) m.press(); };
    const MODES = ['idle', 'locked', 'skip', 'ended', 'buffer', 'error', 'g-bright', 'g-vol', 'g-seek', 'g-long'];
    S.set = (open, mode) => { S.open = open || null; MODES.forEach((c) => root.classList.remove(c)); pause(mode === 'paused'); if (mode && mode !== 'paused') root.classList.add(mode); root.querySelector('[data-act="lock"] use').setAttribute('href', '#i-unlock'); draw(); };
    root.addEventListener('click', (e) => {
      const o = e.target.closest('[data-opt]'), ep = e.target.closest('[data-ep]'), st = e.target.closest('[data-step]'), p = e.target.closest('[data-p]'), a = e.target.closest('[data-act]');
      if (o && S.open) { S.sel[S.open] = +o.dataset.opt; draw(); return; }
      if (ep) { S.ep = +ep.dataset.ep; draw(); return; }
      if (st) { const [k, d] = st.dataset.step.split(':'), M = P[S.open].steps.find((s) => s[0] === k); S.v[k] = M[2] ? clamp(S.v[k] + +d, 0, M[2].length - 1) : clamp(S.v[k] + +d * 5, -50, 50); draw(); return; }
      if (p) { S.open = S.open === p.dataset.p ? null : p.dataset.p; draw(); return; }
      if (a) { const k = a.dataset.act;
        if (k === 'close') { S.open = null; draw(); }
        if (k === 'skip') root.classList.remove('skip');
        if (k === 'play') pause(!root.classList.contains('paused'));
        if (k === 'fav') { a.classList.toggle('faved'); a.querySelector('use').setAttribute('href', a.classList.contains('faved') ? '#i-heartf' : '#i-heart'); }
        if (k === 'lock') { root.classList.toggle('locked'); S.open = null; draw(); }
        return; }
      if (e.target.closest('.panel-host,.ctl,.p-end,.p-err')) return;
      if (S.open) { S.open = null; draw(); } else if (!root.classList.contains('paused') && !root.classList.contains('locked')) root.classList.toggle('idle');
    });
    const tl = root.querySelector('.tl'), set = (p) => { S.p = p; root.style.setProperty('--p', p); root.querySelectorAll('.heat > *').forEach((h, i, all) => h.classList.toggle('on', i / all.length < p)); root.querySelectorAll('.tcur').forEach((t) => { t.textContent = APP.clock(Math.round(p * TOTAL)); }); };
    const at = (e) => { const r = tl.getBoundingClientRect(); set(clamp((e.clientX - r.left) / r.width, 0, 1)); };
    tl.addEventListener('pointerdown', (e) => { root.classList.add('scrub'); at(e); const up = () => { root.classList.remove('scrub'); window.removeEventListener('pointermove', at); window.removeEventListener('pointerup', up); }; window.addEventListener('pointermove', at); window.addEventListener('pointerup', up); e.preventDefault(); e.stopPropagation(); });
    tl.addEventListener('click', (e) => e.stopPropagation());
    root.style.setProperty('--spot', APP.spotOf(T.images.poster)); root.__setP = set; set(S.p); draw();
  },
  setState(root, st) {
    const S = root.__S, M = { idle: 'idle', pause: 'paused', lock: 'locked', skip: 'skip', ended: 'ended', buffer: 'buffer', error: 'error', bright: 'g-bright', vol: 'g-vol', seek: 'g-seek', long: 'g-long' };
    root.__setP(st === 'skip' ? 0.012 : st === 'next' ? 0.955 : st === 'ended' ? 1 : POS / TOTAL);
    S.set(P[st] || st === 'next' ? st : null, M[st]);
    if (['bright', 'vol', 'seek', 'long', 'buffer'].includes(st)) root.classList.add('idle'); if (st === 'buffer') root.classList.remove('idle');
  },
});
})();
