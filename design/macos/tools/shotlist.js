// [name, query, w, h, steps]. 默认窗口 = MacBook Air 13″ 1470 × 956；-pro = MacBook Pro 14″ 1512 × 982；-xl = 外接 27″ 2560 × 1440；-sm = 最小窗口 1100 × 720。
// 以 @ 开头的是「整块屏幕」壳 screen.html（菜单栏、刘海那一条、画中画小窗）。
const AIR = [1470, 956], PRO = [1512, 982], XL = [2560, 1440], SM = [1100, 720];
const L = [];
const add = (name, q, w, h, steps) => L.push([name, q, w, h, steps]);
const st = (p, list, extra) => list.forEach((s) => add(`${p}${s ? '-' + s : ''}`, `p=${p}${s ? '&st=' + s : ''}${extra || ''}`));
// 窗口本体
st('home', ['', 'toc', 'hover', 'end']); add('home-pro', 'p=home', ...PRO); add('home-xl', 'p=home', ...XL); add('home-sm', 'p=home', ...SM); add('home-sm-toc', 'p=home&st=toc', ...SM);
add('home-blur', 'p=home&blur=1'); add('home-fs', 'p=home&fs=1', ...PRO);
add('home-tl-hover', 'p=home', ...AIR, async (page) => { await page.hoverSel('.mast .tlights a.m'); await page.wait(250); });
// ⌘ 键真的按一下（CDP 的 modifiers：4 = ⌘）：⌘K → 搜索页、⌘3 → 片库、⌘R → 付印新刊（期号滚成 319）
const META = 4;
add('home-cmd-k', 'p=home', ...AIR, async (page) => { await page.key('k', META); await page.ready(); console.log('  ⌘K →', await page.eval('location.search')); });
add('home-cmd-3', 'p=home', ...AIR, async (page) => { await page.key('3', META); await page.ready(); console.log('  ⌘3 →', await page.eval('location.search')); });
add('home-cmd-r', 'p=home', ...AIR, async (page) => { await page.key('r', META); await page.wait(1400); console.log('  ⌘R → vol.next =', await page.eval('!!document.querySelector(".mast .vol.next")')); });
add('settings-play-keys', 'p=settings&st=play&scroll=1100');
st('discover', ['']); st('libs', ['']); st('wall', ['', 'filter']); add('wall-sm', 'p=wall', ...SM); st('search', ['', 'results']);
st('series', ['', 'eps']); add('series-pro', 'p=series', ...PRO); st('movie', ['']); st('person', ['']); st('me', ['']); st('account', ['']);
st('settings', ['', 'play', 'about', 'cache']); st('servers', ['', 'pwd']); st('dialogs', ['', 'ctx']); st('errors', ['']);
st('player', ['', 'idle', 'pause', 'sub', 'quality', 'err', 'pip']); add('player-sm', 'p=player', ...SM); add('player-narrow', 'p=player', 640, 420); add('player-full', 'p=player&full=1', ...PRO); add('player-blur', 'p=player&blur=1');
// 整块屏幕：菜单栏 / 系统全屏 + 菜单栏 / 刘海屏全屏播放 / 正在播放 / 画中画小窗
add('screen-menu', '@screen.html?p=home&menu=1&annot=1'); add('screen-menu-app', '@screen.html?p=home&menu=1&annot=1&open=0'); add('screen-menu-file', '@screen.html?p=home&menu=1&annot=1&open=1');
add('screen-menu-edit', '@screen.html?p=home&menu=1&annot=1&open=2'); add('screen-menu-view', '@screen.html?p=wall&menu=1&annot=1&open=3'); add('screen-menu-play', '@screen.html?p=player&menu=1&annot=1&open=4');
add('screen-menu-window', '@screen.html?p=player&menu=1&annot=1&open=5'); add('screen-menu-help', '@screen.html?p=home&menu=1&annot=1&open=6');
add('screen-fs-menu', '@screen.html?p=home&fs=1&menu=1', ...PRO);
add('screen-notch', '@screen.html?p=player&notch=1', ...PRO); add('screen-notch-idle', '@screen.html?p=player&st=idle&notch=1', ...PRO); add('screen-notch-sub', '@screen.html?p=player&st=sub&notch=1', ...PRO);
add('screen-notch-menu', '@screen.html?p=player&notch=1&menu=1', ...PRO); add('screen-notch-home', '@screen.html?p=home&notch=1', ...PRO);
add('screen-np', '@screen.html?p=player&menu=1&np=1'); add('screen-pip', '@screen.html?p=player&st=pip&pipwin=1&menu=1');
// 画廊
add('_gallery', '@index.html?g=首页', 1720, 1000); add('_gallery-menu', '@index.html?g=播放器&open=4', 1720, 1000);
module.exports = L;
