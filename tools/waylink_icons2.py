# Waylink app icons, round 2: Turn (custom mark) plus four built from UIcons glyphs.
# Run from the repo root: python3 tools/waylink_icons2.py
import json, re
D = 'img/logos/'
src = open('js/icons-uicons.js').read()
ICONS = json.loads(re.sub(r",\s*}$", "}", re.search(r"export const ICONS = (\{.*\});", src, re.S).group(1)))

def tile(bg, body, defs=''):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs>{defs}</defs>'
            f'<rect width="512" height="512" rx="114" fill="{bg}"/>{body}</svg>')

def glyph(name, color, size=264, dx=0, dy=0):
    s = size / 300; o = (512 - size) / 2
    return f'<g transform="translate({o+dx} {o+dy}) scale({s:.4f})" fill="{color}">{ICONS[name]}</g>'

I = {}
# TURN: a W whose last stroke rises and turns right into an arrow, the turn-by-turn sign and the W in one.
wt = 'M96 168L160 352L230 228L300 352L338 252Q356 200 404 200'
I['wl-turn'] = ('Turn', tile('url(#g)',
    f'<g transform="translate(256 256) scale(1.08) translate(-274 -270)"><path d="{wt}" fill="none" stroke="#fff" stroke-width="46" stroke-linejoin="round" stroke-linecap="round"/>'
    '<path d="M396 140L468 200L396 260Z" fill="#fff" stroke="#fff" stroke-width="10" stroke-linejoin="round"/></g>',
    '<linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5B78F6"/><stop offset="1" stop-color="#3550D8"/></linearGradient>'))
# ROAD: an open road running to the horizon, dashed centre line.
I['wl-road'] = ('Road', tile('#14213D',
    '<path d="M226 112H286L410 424H102Z" fill="#33456B"/>'
    '<path d="M256 136V170M256 206V256M256 300V372" stroke="#FFC845" stroke-width="18" stroke-linecap="round"/>'
    '<path d="M226 112L102 424M286 112L410 424" stroke="#E9EDF5" stroke-width="14" stroke-linecap="round"/>'))
# CHAIN: two links of a bike chain, locked together.
I['wl-chain'] = ('Chain', tile('#F4E9E2',
    '<g transform="rotate(-45 256 256)" fill="none" stroke-width="40">'
    '<rect x="100" y="196" width="196" height="120" rx="60" stroke="#BF4128"/>'
    '<rect x="216" y="196" width="196" height="120" rx="60" stroke="#1C1B19"/>'
    '<path d="M236 196a60 60 0 0 1 60 60" stroke="#BF4128"/></g>'))
I['wl-nav'] = ('Arrow', tile('#16181D', glyph('nav', '#F2F3F5', 250)))
I['wl-route'] = ('Route', tile('#0F2E2A', glyph('route', '#5EE6B8', 260)))
for k, (nm, svg) in I.items():
    open(D + k + '.svg', 'w').write(svg)
print([(k, v[0]) for k, v in I.items()])
