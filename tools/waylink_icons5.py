# Waylink app icon (final): the route glyph from UIcons on a tile tinted by each accent colour.
# Writes img/logos/wl-route-<accent>.svg; PNGs are rendered separately. Run from the repo root.
import json, re
D = 'img/logos/'
src = open('js/icons-uicons.js').read()
ICONS = json.loads(re.sub(r",\s*}$", "}", re.search(r"export const ICONS = (\{.*\});", src, re.S).group(1)))
ACC = {'rust': '#BF4128', 'wine': '#B3172A', 'blue': '#1D56CF', 'slate': '#5D697D', 'amber': '#C98316', 'mint': '#2A8570'}
def mix(h, to, t):
    a = [int(h[i:i+2], 16) for i in (1, 3, 5)]; b = [int(to[i:i+2], 16) for i in (1, 3, 5)]
    return '#' + ''.join(f'{round(x + (y - x) * t):02X}' for x, y in zip(a, b))
size = 270; s = size / 300; o = (512 - size) / 2
for k, col in ACC.items():
    bg, fg = mix(col, '#000000', 0.58), mix(col, '#FFFFFF', 0.55)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><rect width="512" height="512" rx="114" fill="{bg}"/>'
           f'<g transform="translate({o} {o}) scale({s:.4f})" fill="{fg}">{ICONS["route"]}</g></svg>')
    open(f'{D}wl-route-{k}.svg', 'w').write(svg)
print(list(ACC))
