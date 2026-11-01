// Renders the windows mockup's popupCap and holly as test references.
// node render-references.js ../../../../docs/mockup/jingle-code-windows.html references.txt
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
const script = html.slice(html.lastIndexOf('<script>') + 8, html.lastIndexOf('</script>'));
const src = script.slice(0, script.indexOf('const canvases'));
function ctx(W, H) {
  const px = new Array(W * H).fill('.');
  return { px, W, H, fillStyle: '', fillRect(x, y, w, h) { for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) if (xx >= 0 && yy >= 0 && xx < W && yy < H) px[yy * W + xx] = this.fillStyle.toLowerCase(); } };
}
const lines = [];
eval(src.replace(/^let frame = 0, paused = .*$/m, 'var frame = 0;') + `
function dump(name, c) { const rows = []; for (let y = 0; y < c.H; y++) rows.push(c.px.slice(y * c.W, (y + 1) * c.W).map(p => p === '.' ? '.' : p).join(' ')); lines.push(name); rows.forEach(r => lines.push(r)); }
for (const [w, h, f] of [[180, 4, 0], [90, 3, 1], [70, 4, 31], [20, 4, 60], [7, 3, 2]]) { frame = f; const c = ctx(w, h); D.popupCap(c, w, h); dump('cap ' + w + ' ' + h + ' ' + f, c); }
for (const f of [0, 10]) { frame = f; const c = ctx(13, 10); D.holly(c, 13, 10); dump('holly ' + f, c); }
`);
fs.writeFileSync(process.argv[3], lines.join('\n') + '\n');
console.log(lines.length, 'lines');
