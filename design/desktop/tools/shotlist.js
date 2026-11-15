// [name, query, w, h, steps]. Default window 1440 x 900; -xl = 3168 x 1440 (the user's test resolution), -sm = 1100 x 720.
const XL = [3168, 1440], SM = [1100, 720];
const L = [];
const add = (name, q, w, h, steps) => L.push([name, q, w, h, steps]);
const st = (p, list, extra) => list.forEach((s) => add(`${p}${s ? '-' + s : ''}`, `p=${p}${s ? '&st=' + s : ''}${extra || ''}`));
const sizes = (p, s, name) => { add(`${name || p}-xl`, `p=${p}${s ? '&st=' + s : ''}`, ...XL); add(`${name || p}-sm`, `p=${p}${s ? '&st=' + s : ''}`, ...SM); };
st('home', ['', 'mid', 'fav', 'toc', 'hover', 'focus', 'rows', 'end', 'load']); sizes('home'); sizes('home', 'toc', 'home-toc'); sizes('home', 'rows', 'home-rows');
st('discover', ['', 'idx', 'top']);
st('libs', ['']);
st('wall', ['', 'tv', 'anime', 'filter', 'sort', 'az', 'list', 'loading', 'empty']); sizes('wall'); sizes('wall', 'filter', 'wall-filter');
st('colls', ['']); st('coll', ['', 'av', 'fb']);
st('search', ['', 'typing', 'results', 'long', 'none']);
st('series', ['', 'eps', 'mark', 'op', 'seasons', 'op21', 'cast', 'ep', 'daima', 'tennis', 'guyu']); sizes('series'); sizes('series', 'eps', 'series-eps'); sizes('series', 'seasons', 'series-seasons');
st('movie', ['', 'ver', 'streams', 'cast', 'nologo', 'short', 'long']);
st('person', ['', 'one', 'works']);
st('me', ['', 'fav', 'favempty']);
st('account', ['', 'switch']);
st('settings', ['', 'ui', 'play', 'strm', 'sub', 'dm', 'cache', 'acct', 'about', 'dd']);
st('servers', ['', 'add', 'login', 'pwd']);
st('dialogs', ['', 'ctx', 'mng', 'del']);
st('player', ['', 'idle', 'pause', 'hover', 'sub', 'audio', 'speed', 'quality', 'dm', 'eps', 'skip', 'next', 'buf', 'err']); sizes('player'); sizes('player', 'eps', 'player-eps');
add('player-full', 'p=player&full=1', 1920, 1080); add('player-full-sub', 'p=player&st=sub&full=1', 1920, 1080); add('player-narrow', 'p=player', 640, 420); add('player-narrow-sub', 'p=player&st=sub', 640, 420);
st('errors', ['', 'server', 'auth', 'emptylib']);
add('_gallery', '@index.html?g=首页', 1720, 1000);
module.exports = L;
// contact sheets (second looks): node tools/shots.js sheet-
[['A', 'series-op21,series-guyu,series-tennis,series-daima'], ['B', 'movie-nologo,movie-short,movie-ver,movie-streams'], ['C', 'player-pause,player-skip,player-err,player-hover'], ['D', 'player-idle,player-buf,player-dm,player-quality'],
 ['E', 'home-load,home-end,home-rows,home-focus'], ['F', 'wall,wall-tv,wall-list,wall-empty'], ['G', 'search,search-typing,search-long,search-none'], ['H', 'me,me-fav,me-favempty,account-switch'],
 ['I', 'settings-ui,settings-sub,settings-cache,person'], ['J', 'servers,servers-add,dialogs,dialogs-ctx'], ['K', 'errors-server,errors-auth,errors-emptylib,coll-fb'], ['L', 'discover-idx,discover-top,libs,colls'],
 ['M', 'home-sm-toc,wall-sm,series-sm,player-sm'], ['N', 'settings-play,settings-strm,settings-dm,settings-acct'], ['O', 'player-sub,player-audio,player-speed,player-full'], ['P', 'series-mark,series-cast,movie-cast,person-works'],
 ['Q', 'wall-anime,wall-filter,wall-sort,wall-loading'], ['R', 'home-mid,home-toc,home-hover,coll-av'], ['S', 'settings,settings-about,settings-dd,servers-login'], ['T', 'dialogs-mng,dialogs-del,player-narrow,player-full-sub']]
  .forEach(([n, f]) => L.push(['sheet-' + n, '@tools/sheet.html?f=' + f, 2880, 1800]));
