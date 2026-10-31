// Renders the village mockup's scenes and residents as test references. node mock2.js <mockup.html> <outdir>
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
let src = html.slice(html.indexOf('<script>') + 8, html.indexOf('</script>'));
src = src.slice(0, src.indexOf('/* ---------- каталог'));
src = src.replace('function sky(c, W, r, xFrom = 0) {', 'function sky(c, W, r, xFrom = 0) { return;');
Math.random = () => 1; // the planner starts nothing by itself
globalThis.paused = false;
function ctx(W, H) {
  const px = new Array(W * H).fill('.');
  return { px, fillStyle: '', fillRect(x, y, w, h) { for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) if (xx >= 0 && yy >= 0 && xx < W && yy < H) px[yy * W + xx] = this.fillStyle.toLowerCase(); } };
}
const out = {};
eval(src + `
const fns = { v1, v2, v3, v4 };
function render(vid, active) { sceneState[vid] = { active }; const c = ctx(W, H); fns[vid](c); return c.px; }
function rle(px) { const rows = []; for (let y = 0; y < H; y++) { const row = []; let prev = null, n = 0;
  for (let x = 0; x < W; x++) { const p = px[y * W + x]; if (p === prev) n++; else { if (prev !== null) row.push(prev + '*' + n); prev = p; n = 1; } }
  row.push(prev + '*' + n); rows.push(row.join(' ')); } return rows.join(String.fromCharCode(10)); }
seed = 7;
for (const vid of Object.keys(fns)) {
  for (const [ev, fr] of [[true, 13], [false, 77]]) { evening = ev; frame = fr; out[vid + '-' + (ev ? 'evening' : 'day') + '-' + fr] = rle(render(vid, {})); }
  const lines = [];
  frame = 300;
  for (const s of SCENES[vid]) {
    const samples = [0, Math.floor(s.dur / 3), Math.floor(s.dur * 2 / 3), s.dur - 1].map(t => [t, true]).concat([[Math.floor(s.dur / 2), false]]);
    for (const [t, ev] of samples) {
      evening = ev;
      const base = render(vid, {}), with_ = render(vid, { [s.id]: frame - t });
      const diff = []; for (let i = 0; i < base.length; i++) if (base[i] !== with_[i]) diff.push((i % W) + ',' + Math.floor(i / W) + ',' + with_[i]);
      lines.push(s.id + ' ' + t + ' ' + (ev ? 'evening' : 'day') + ' ' + diff.join(';'));
    }
  }
  out[vid + '-residents'] = lines.join(String.fromCharCode(10));
}`);
const dir = process.argv[3]; fs.mkdirSync(dir, { recursive: true });
for (const k in out) fs.writeFileSync(dir + '/' + k + '.txt', out[k] + '\n');
console.log(Object.keys(out).length, 'references');
