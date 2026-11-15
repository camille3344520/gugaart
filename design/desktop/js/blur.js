/* Pre-blurred pictures (home cover spread, titles without a backdrop). Blurring in a canvas (not with a CSS filter) keeps the page and the glass
 * in step: the glass adapter redraws <img> elements and ignores CSS filters, so a CSS blur would look sharp through the glass. <img data-blur="key"> */
(function () {
'use strict';
const IMG = window.IMG, done = {};
APP.blurKey = (key) => done[key] || (done[key] = APP.imgOf(key).then((im) => {
  const c = document.createElement('canvas'), w = 640, h = 360; c.width = w; c.height = h; const x = c.getContext('2d');
  const k = Math.max((w + 120) / (im.naturalWidth || 1), (h + 120) / (im.naturalHeight || 1)), dw = im.naturalWidth * k, dh = im.naturalHeight * k;
  x.fillStyle = '#100E0B'; x.fillRect(0, 0, w, h); x.filter = 'blur(16px) saturate(1.2) brightness(.82)'; x.drawImage(im, (w - dw) / 2, (h - dh) * 0.3, dw, dh);
  IMG['b_' + key] = c.toDataURL('image/jpeg', 0.86); return 'b_' + key; }));
const fill0 = APP.fill;
APP.fill = async (root) => {
  await Promise.all([...(root || document).querySelectorAll('img[data-blur]')].map(async (im) => { const k = await APP.blurKey(im.dataset.blur); im.dataset.img = k; im.removeAttribute('data-blur'); }));
  return fill0(root);
};
})();
