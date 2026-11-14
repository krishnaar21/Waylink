# Waylink app icons, round 4: Turn stays the custom mark; the rest are UIcons glyphs on coloured tiles.
# Run from the repo root: python3 tools/waylink_icons4.py
import json, re
D = 'img/logos/'
src = open('js/icons-uicons.js').read()
ICONS = json.loads(re.sub(r",\s*}$", "}", re.search(r"export const ICONS = (\{.*\});", src, re.S).group(1)))
def tile(bg, body, defs=''):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs>{defs}</defs>'
            f'<rect width="512" height="512" rx="114" fill="{bg}"/>{body}</svg>')
def glyph(name, color, size=270):
    s = size / 300; o = (512 - size) / 2
    return f'<g transform="translate({o} {o}) scale({s:.4f})" fill="{color}">{ICONS[name]}</g>'
L = {
    'wl-route': ('Route', tile('#123D33', glyph('route', '#7FE0BE'))),
    'wl-nav': ('Arrow', tile('#F3E3C3', glyph('nav', '#B23A20', 250))),
    'wl-pin': ('Pin', tile('#1D2B4F', glyph('pin', '#8FB2FF'))),
    'wl-gauge': ('Gauge', tile('#161514', glyph('speed', '#F6C177'))),
    'wl-link': ('Link', tile('#EFE7FF', glyph('chain', '#5B3FC4', 250))),
}
for k, (nm, svg) in L.items():
    open(D + k + '.svg', 'w').write(svg)
print([(k, v[0]) for k, v in L.items()])
