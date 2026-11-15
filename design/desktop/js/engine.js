/*
 * demo3 glass engine (WebGL2).
 *
 * The glass shader FS_GLASS is the shader of the user-confirmed sample (experiments/ui-wave/glass/demo/glass-demo.html,
 * liquid_glass_widgets v1.16 classic) = native/designsystem GlassShaders.kt, step by step. Only things that are NOT the glass maths
 * were generalised: up to 16 shapes per pass, a blur source per shape (sigma 1.5 / 2.5 / 20 / 24 dp ...), the text-contrast backer
 * (GlassModel LEGIBILITY_BACKER, between the tint pull and the highlights, exactly like GlassShaders.kt) and the player's flat shade.
 *
 * Frame = scene (scrolling 2D texture) -> stages: glass | overlay (2D canvas composited with alpha) | scrim | snapshot.
 * Coordinates handed to the engine are dp; the engine multiplies by dpr.
 */
(function (G) {
'use strict';
const MAXS = 16;

const VS = `#version 300 es
in vec2 aPos; void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }`;

// fixed page backdrop (uBg) + scrolling content (uContent, premultiplied alpha); the status-bar strip fades the CONTENT out so the backdrop shows
const FS_SCENE = `#version 300 es
precision highp float;
uniform sampler2D uContent; uniform sampler2D uBgTex; uniform vec2 uRes; uniform float uScroll; uniform float uContentH; uniform vec2 uFade;
out vec4 o;
void main(){
  vec2 p = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y);
  float cy = p.y + uScroll;
  vec2 uv = vec2(p.x / uRes.x, 1.0 - cy / uContentH);
  vec4 s = texture(uContent, clamp(uv, vec2(0.0), vec2(1.0)));
  if (uFade.y > uFade.x) s *= smoothstep(uFade.x, uFade.y, p.y);
  vec3 base = texture(uBgTex, vec2(gl_FragCoord.x / uRes.x, gl_FragCoord.y / uRes.y)).rgb;
  o = vec4(s.rgb + base * (1.0 - s.a), 1.0);
}`;

const FS_COPY = `#version 300 es
precision highp float;
uniform sampler2D uSrc; uniform vec2 uOut;
out vec4 o;
void main(){ o = texture(uSrc, gl_FragCoord.xy / uOut); }`;

const FS_BLUR = `#version 300 es
precision highp float;
uniform sampler2D uSrc; uniform vec2 uOut; uniform vec2 uDir; uniform float uSigma;
out vec4 o;
void main(){
  vec2 uv = gl_FragCoord.xy / uOut;
  float s = uSigma;
  if (s < 0.05) { o = texture(uSrc, uv); return; }
  int R = int(ceil(s * 3.0));
  vec4 acc = vec4(0.0); float ws = 0.0;
  for (int k = -R; k <= R; k++) {
    float w = exp(-float(k * k) / (2.0 * s * s));
    acc += texture(uSrc, uv + uDir * float(k) / uOut) * w; ws += w;
  }
  o = acc / ws;
}`;

// the page recedes while a panel grows: shrink around the centre (uScale) and dim (uDim) what has been composed so far
const FS_RECEDE = `#version 300 es
precision highp float;
uniform sampler2D uSrc; uniform vec2 uOut; uniform float uScale; uniform float uDim;
out vec4 o;
void main(){
  vec2 uv = gl_FragCoord.xy / uOut;
  vec2 s = (uv - 0.5) / uScale + 0.5;
  o = vec4(texture(uSrc, clamp(s, vec2(0.0005), vec2(0.9995))).rgb * (1.0 - uDim), 1.0);
}`;

const FS_FLAT = `#version 300 es
precision highp float;
uniform vec4 uColor;
out vec4 o;
void main(){ o = uColor; }`;

// premultiplied overlay composited with ONE, ONE_MINUS_SRC_ALPHA
const FS_OVER = `#version 300 es
precision highp float;
uniform sampler2D uSrc; uniform vec2 uOut;
out vec4 o;
void main(){ o = texture(uSrc, gl_FragCoord.xy / uOut); }`;

const FS_GLASS = `#version 300 es
precision highp float;
uniform sampler2D uSharp; uniform sampler2D uBase;
uniform sampler2D uB0; uniform sampler2D uB1; uniform sampler2D uB2; uniform sampler2D uB3;
uniform vec2 uRes; uniform float uDpr; uniform vec2 uL; uniform int uN;
uniform vec4 uGeo[${MAXS}];  // cx, cy, hw, hh (px, y down)
uniform vec4 uGeo2[${MAXS}]; // radius px, scaleX, scaleY, visibility
uniform vec4 uMat[${MAXS}];  // thickness (library units), refractive index, chromatic, source (0 sharp, 1..4 blur slot)
uniform vec4 uTint[${MAXS}]; // glass colour rgba (straight)
uniform vec4 uLit[${MAXS}];  // lightIntensity, ambientStrength, saturation, fresnelStrength
uniform vec4 uBack[${MAXS}]; // text-contrast backer: peak, from, to, mode (1 darken, -1 lighten, 0 none)
uniform vec4 uShade[${MAXS}];// flat shade over the finished glass (player): rgb, alpha
out vec4 o;
const vec3 LW = vec3(0.2126, 0.7152, 0.0722);
float sdRR(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
vec3 tex(float sel, vec2 p) {
  vec2 uv = clamp(vec2(p.x / uRes.x, 1.0 - p.y / uRes.y), vec2(0.0005), vec2(0.9995));
  if (sel < 0.5) return texture(uSharp, uv).rgb;
  if (sel < 1.5) return texture(uB0, uv).rgb;
  if (sel < 2.5) return texture(uB1, uv).rgb;
  if (sel < 3.5) return texture(uB2, uv).rgb;
  return texture(uB3, uv).rgb;
}
vec3 highlight(vec3 bg) {
  float l = dot(bg, LW); float mx = max(max(bg.r, bg.g), bg.b);
  float a = l * 2.5; a = a / (1.0 + a); float s = mx * 2.5; s = s / (1.0 + s);
  return mix(vec3(1.0), bg / max(l, 0.001), a * s);
}
vec3 saturate3(vec3 c, float s) { float l = dot(c, LW); return clamp(mix(vec3(l), c, s), 0.0, 1.0); }
void main() {
  vec2 p = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y);
  vec3 col = texture(uBase, vec2(gl_FragCoord.x / uRes.x, gl_FragCoord.y / uRes.y)).rgb;
  for (int i = 0; i < ${MAXS}; i++) {
    if (i >= uN) break;
    vec4 g = uGeo[i]; vec4 g2 = uGeo2[i];
    vec2 sc = max(g2.yz, vec2(0.05));
    vec2 q = (p - g.xy) / sc;
    vec2 b = g.zw;
    if (abs(q.x) > b.x + 3.0 || abs(q.y) > b.y + 3.0) continue;
    float r = min(g2.x, min(b.x, b.y));
    float sd = sdRR(q, b, r);
    float cov = clamp(0.5 - sd, 0.0, 1.0) * g2.w;
    if (cov <= 0.0) continue;
    float T = max(uMat[i].x * uDpr / 3.0, 0.001);
    vec2 gr = vec2(sdRR(q + vec2(1.0, 0.0), b, r) - sdRR(q - vec2(1.0, 0.0), b, r),
                   sdRR(q + vec2(0.0, 1.0), b, r) - sdRR(q - vec2(0.0, 1.0), b, r));
    float glen = length(gr); vec2 gn = glen > 1e-5 ? gr / glen : vec2(0.0);
    float sdN = min(sd, 0.0);
    float ncos = max(T + sdN, 0.0) / T; float nsin = sqrt(max(0.0, 1.0 - ncos * ncos));
    vec3 N = normalize(vec3(gn * ncos, nsin));
    float height = sdN < -T ? T : sqrt(max(0.0, T * T - (T + sdN) * (T + sdN)));
    float nh = height / T;
    vec2 disp = vec2(0.0);
    if (dot(N.xy, N.xy) > 1e-6) {
      vec3 rf = refract(vec3(0.0, 0.0, -1.0), N, 1.0 / max(uMat[i].y, 0.001));
      float rl = (height + 8.0 * T) / max(0.001, abs(rf.z));
      disp = rf.xy * rl;
      float maxReach = min(b.x, b.y) * min(sc.x, sc.y);
      float reach = length(disp); if (reach > maxReach) disp *= maxReach / reach;
    }
    float sel = uMat[i].w; float ca = uMat[i].z;
    vec3 rc;
    if (ca < 0.01) rc = tex(sel, p + disp);
    else { float ds = ca * 0.5; rc = vec3(tex(sel, p + disp * (1.0 + ds)).r, tex(sel, p + disp).g, tex(sel, p + disp * (1.0 - ds)).b); }
    vec4 tint = uTint[i];
    vec3 direct = mix(rc, tint.rgb, tint.a);
    float bl = dot(rc, LW); float tl = dot(tint.rgb, LW);
    vec3 tinted = clamp(tint.rgb + (bl - tl), 0.0, 1.0);
    float chroma = max(max(tint.r, tint.g), tint.b) - min(min(tint.r, tint.g), tint.b);
    vec3 c = mix(direct, mix(rc, tinted, tint.a), clamp(chroma * 8.0, 0.0, 1.0));
    float ad = mix(1.2, 0.8, bl);
    c = saturate3(c, uLit[i].z * ad);
    c = mix(c, tint.rgb, tint.a * 0.12 * (ad - 1.0));
    // our text-contrast backer (GlassModel LEGIBILITY_BACKER): dark theme darkens a bright backdrop, light theme lightens a dark one
    vec4 bk = uBack[i];
    if (bk.w > 0.5) c = c * (1.0 - bk.x * smoothstep(bk.y, bk.z, bl));
    else if (bk.w < -0.5) c = mix(c, vec3(1.0), bk.x * (1.0 - smoothstep(bk.y, bk.z, bl)));
    float tS = clamp(40.0 * (uDpr / 3.0) / max(T, 1.0), 1.0, 4.0);
    float eF = 1.0 - smoothstep(0.0, mix(0.8, 0.5, 1.0 / tS), nh);
    if (eF > 0.01 && (uLit[i].x > 0.0 || uLit[i].y > 0.0)) {
      float m = max(0.0, dot(gn, uL)); float op = max(0.0, dot(gn, -uL));
      float tot = m + op * 0.8;
      float raw = (tot * sqrt(tot) * uLit[i].x * 3.0 + uLit[i].y * 0.5) * eF * tS * 0.8;
      c = mix(c, highlight(rc), raw / (1.0 + raw));
    }
    c = clamp(c + vec3((1.0 - N.z) * eF * 0.12 * uLit[i].w), 0.0, 1.0);
    c = mix(c, uShade[i].rgb, uShade[i].a);
    col = mix(col, c, cov);
  }
  o = vec4(col, 1.0);
}`;

G.createEngine = function (canvas, W, H, dpr) {
  const RW = Math.round(W * dpr), RH = Math.round(H * dpr);
  canvas.width = RW; canvas.height = RH;
  const gl = canvas.getContext('webgl2', { antialias: false, premultipliedAlpha: false, preserveDrawingBuffer: true, alpha: false });
  if (!gl) return null;

  function compile(type, src) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
    return s;
  }
  function program(fs) {
    const p = gl.createProgram();
    gl.attachShader(p, compile(gl.VERTEX_SHADER, VS)); gl.attachShader(p, compile(gl.FRAGMENT_SHADER, fs));
    gl.bindAttribLocation(p, 0, 'aPos'); gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    const u = {}; const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
    for (let i = 0; i < n; i++) { const name = gl.getActiveUniform(p, i).name.replace(/\[0\]$/, ''); u[name] = gl.getUniformLocation(p, name); }
    return { p, u };
  }
  const PR = {
    scene: program(FS_SCENE), recede: program(FS_RECEDE), copy: program(FS_COPY), blur: program(FS_BLUR), flat: program(FS_FLAT), over: program(FS_OVER), glass: program(FS_GLASS),
  };
  const quad = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, quad);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);

  function makeTex(w, h) {
    const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return t;
  }
  function target(w, h) {
    const t = makeTex(w, h); const f = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, f); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
    return { t, f, w, h };
  }
  function ensure(tg, w, h) {
    if (tg.w === w && tg.h === h) return tg;
    gl.deleteTexture(tg.t); gl.deleteFramebuffer(tg.f);
    return target(w, h);
  }
  function bindTex(unit, tex) { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, tex); }
  function draw(to, w, h) { gl.bindFramebuffer(gl.FRAMEBUFFER, to ? to.f : null); gl.viewport(0, 0, w, h); gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4); }

  let TA = target(RW, RH), TB = target(RW, RH), TRAW = target(RW, RH);
  const D = { 2: target(Math.ceil(RW / 2), Math.ceil(RH / 2)), 4: target(Math.ceil(RW / 4), Math.ceil(RH / 4)) };
  const TMP = { 1: target(RW, RH), 2: target(Math.ceil(RW / 2), Math.ceil(RH / 2)), 4: target(Math.ceil(RW / 4), Math.ceil(RH / 4)) };
  const slots = [0, 1, 2, 3].map(() => target(4, 4));
  const ovTex = {}; // key -> texture
  let sceneTex = makeTex(1, 1), bgTex = makeTex(1, 1), sceneH = H;

  function copy(srcTex, dstTarget, w, h) {
    gl.useProgram(PR.copy.p); bindTex(0, srcTex); gl.uniform1i(PR.copy.u.uSrc, 0); gl.uniform2f(PR.copy.u.uOut, w, h);
    gl.disable(gl.BLEND); draw(dstTarget, w, h);
  }

  function setScene(cnv, hdp) {
    sceneH = hdp;
    gl.bindTexture(gl.TEXTURE_2D, sceneTex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true); gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, cnv);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
  }
  function setBg(cnv) {
    gl.bindTexture(gl.TEXTURE_2D, bgTex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true); gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, cnv);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  }
  function setOverlay(key, cnv) {
    if (!ovTex[key]) ovTex[key] = makeTex(1, 1);
    gl.bindTexture(gl.TEXTURE_2D, ovTex[key]);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true); gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, gl.RGBA, gl.UNSIGNED_BYTE, cnv);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
  }

  // blur factor: sigma in device px -> downscale factor so the kernel stays <= 48 taps a side
  function blurFactor(sigmaPx) { return sigmaPx < 4 ? 1 : (sigmaPx < 16 ? 2 : 4); }

  function prepBlurs(src, shapes) {
    const list = [];
    for (const s of shapes) if (s.blur > 0.05 && list.indexOf(s.blur) < 0) list.push(s.blur);
    list.sort((a, b) => a - b);
    while (list.length > 4) list.splice(1, 1); // never more than 4 sources: drop the one next to the smallest
    let need2 = false, need4 = false;
    for (const s of list) { const f = blurFactor(s * dpr); if (f >= 2) need2 = true; if (f === 4) need4 = true; }
    if (need2) copy(src.t, D[2], D[2].w, D[2].h);
    if (need4) copy(D[2].t, D[4], D[4].w, D[4].h);
    const outs = [];
    list.forEach((sg, k) => {
      const f = blurFactor(sg * dpr);
      const w = Math.ceil(RW / f), h = Math.ceil(RH / f);
      const input = f === 1 ? src : D[f];
      slots[k] = ensure(slots[k], w, h);
      gl.useProgram(PR.blur.p); gl.uniform1i(PR.blur.u.uSrc, 0); gl.uniform2f(PR.blur.u.uOut, w, h);
      gl.uniform1f(PR.blur.u.uSigma, sg * dpr / f);
      bindTex(0, input.t); gl.uniform2f(PR.blur.u.uDir, 1, 0); draw(TMP[f], w, h);
      bindTex(0, TMP[f].t); gl.uniform2f(PR.blur.u.uDir, 0, 1); draw(slots[k], w, h);
      outs.push(slots[k]);
    });
    return { list, outs };
  }

  const A = {
    geo: new Float32Array(MAXS * 4), geo2: new Float32Array(MAXS * 4), mat: new Float32Array(MAXS * 4), tint: new Float32Array(MAXS * 4),
    lit: new Float32Array(MAXS * 4), back: new Float32Array(MAXS * 4), shade: new Float32Array(MAXS * 4),
  };

  function glassPass(sharp, base, dst, shapes, blurs) {
    const n = Math.min(MAXS, shapes.length);
    for (let i = 0; i < n; i++) {
      const s = shapes[i];
      const sel = s.blur > 0.05 ? (blurs.list.indexOf(s.blur) >= 0 ? blurs.list.indexOf(s.blur) + 1 : 1) : 0;
      A.geo.set([s.cx * dpr, s.cy * dpr, s.hw * dpr, s.hh * dpr], i * 4);
      A.geo2.set([s.r * dpr, s.sx, s.sy, s.vis], i * 4);
      A.mat.set([s.th, s.n, s.ca || 0, sel], i * 4);
      A.tint.set(s.tint, i * 4);
      A.lit.set(s.lit, i * 4);
      A.back.set(s.back || [0, 0, 0, 0], i * 4);
      A.shade.set(s.shade || [0, 0, 0, 0], i * 4);
    }
    const u = PR.glass.u;
    gl.useProgram(PR.glass.p); gl.disable(gl.BLEND);
    bindTex(0, sharp.t); bindTex(1, base.t);
    for (let k = 0; k < 4; k++) bindTex(2 + k, (blurs.outs[k] || slots[k]).t);
    gl.uniform1i(u.uSharp, 0); gl.uniform1i(u.uBase, 1); gl.uniform1i(u.uB0, 2); gl.uniform1i(u.uB1, 3); gl.uniform1i(u.uB2, 4); gl.uniform1i(u.uB3, 5);
    gl.uniform2f(u.uRes, RW, RH); gl.uniform1f(u.uDpr, dpr);
    const a = 0.75 * Math.PI; gl.uniform2f(u.uL, Math.cos(a), -Math.sin(a)); // GlassModel.lightVector(0.75 pi)
    gl.uniform1i(u.uN, n);
    gl.uniform4fv(u.uGeo, A.geo); gl.uniform4fv(u.uGeo2, A.geo2); gl.uniform4fv(u.uMat, A.mat); gl.uniform4fv(u.uTint, A.tint);
    gl.uniform4fv(u.uLit, A.lit); gl.uniform4fv(u.uBack, A.back); gl.uniform4fv(u.uShade, A.shade);
    draw(dst, RW, RH);
  }

  function render(fr) {
    // scene
    gl.useProgram(PR.scene.p); gl.disable(gl.BLEND);
    bindTex(0, sceneTex); gl.uniform1i(PR.scene.u.uContent, 0); bindTex(1, bgTex); gl.uniform1i(PR.scene.u.uBgTex, 1);
    gl.uniform2f(PR.scene.u.uRes, RW, RH); gl.uniform1f(PR.scene.u.uScroll, (fr.scroll || 0) * dpr); gl.uniform1f(PR.scene.u.uContentH, sceneH * dpr);
    gl.uniform2f(PR.scene.u.uFade, (fr.fade ? fr.fade[0] : 0) * dpr, (fr.fade ? fr.fade[1] : 0) * dpr);
    draw(TA, RW, RH);
    let cur = TA, oth = TB;
    for (const st of fr.stages) {
      if (st.t === 'snap') { copy(cur.t, TRAW, RW, RH); }
      else if (st.t === 'ov') {
        if (!ovTex[st.k]) continue;
        gl.useProgram(PR.over.p); bindTex(0, ovTex[st.k]); gl.uniform1i(PR.over.u.uSrc, 0); gl.uniform2f(PR.over.u.uOut, RW, RH);
        gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); draw(cur, RW, RH); gl.disable(gl.BLEND);
      } else if (st.t === 'recede') {
        gl.useProgram(PR.recede.p); bindTex(0, cur.t); gl.uniform1i(PR.recede.u.uSrc, 0); gl.uniform2f(PR.recede.u.uOut, RW, RH);
        gl.uniform1f(PR.recede.u.uScale, st.scale); gl.uniform1f(PR.recede.u.uDim, st.dim);
        gl.disable(gl.BLEND); draw(oth, RW, RH); const t2 = cur; cur = oth; oth = t2;
      } else if (st.t === 'scrim') {
        gl.useProgram(PR.flat.p); const c = st.rgb;
        gl.uniform4f(PR.flat.u.uColor, c[0] * st.a, c[1] * st.a, c[2] * st.a, st.a);
        gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA); draw(cur, RW, RH); gl.disable(gl.BLEND);
      } else if (st.t === 'glass') {
        // more than 16 shapes in one stage: further passes over the result of the previous one (shapes of a stage never overlap)
        for (let i = 0; i < st.shapes.length; i += MAXS) {
          const chunk = st.shapes.slice(i, i + MAXS);
          const sharp = st.raw ? TRAW : cur;
          const blurs = prepBlurs(sharp, chunk);
          glassPass(sharp, cur, oth, chunk, blurs);
          const t = cur; cur = oth; oth = t;
        }
      }
    }
    gl.useProgram(PR.copy.p); bindTex(0, cur.t); gl.uniform1i(PR.copy.u.uSrc, 0); gl.uniform2f(PR.copy.u.uOut, RW, RH);
    gl.disable(gl.BLEND); draw(null, RW, RH);
  }

  return { gl, RW, RH, dpr, W, H, setScene, setBg, setOverlay, render, MAXS };
};
})(window.G3 = window.G3 || {});
