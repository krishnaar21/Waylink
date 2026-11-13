# Crafted app icon styles: Leather (debossed stitched patch), ASCII (neon on black), Wool (real yarn).
# Writes img/logos/wl-icon-{leather,ascii,wool}.svg. PNGs come from tools/icons_png.py.
import random, math
D = "M2.5 44C3 64 12 80 25 80C37 80 42 64 46 50C48.8 40.5 54.5 31 54 22C53.6 13.5 39 12.5 38.6 22C38.2 31 43.4 40.5 46.8 50C51 64 58 80 71 80C84 80 90 56 91.5 26L91.5 14"
T = 'translate(256 262) scale(3.1) translate(-47 -48)'
def path(stroke, w, extra=''): return f'<path d="{D}" fill="none" stroke="{stroke}" stroke-width="{w}"{'' if 'linecap' in extra else ' stroke-linecap="round"'} stroke-linejoin="round" {extra}/>'
def svg(body): return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">{body}</svg>'

# Leather: a pebble-grain patch, dark saddle stitching, the w pressed in dark.
leather = svg(f'''<defs>
<clipPath id="lc"><rect width="512" height="512" rx="114"/></clipPath>
<radialGradient id="lp" cx="42%" cy="36%" r="72%"><stop offset="0" stop-color="#A2602F"/><stop offset=".65" stop-color="#7C4320"/><stop offset="1" stop-color="#5A2E14"/></radialGradient>
<filter id="lg" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".11" numOctaves="3" seed="11" result="n"/>
<feDiffuseLighting in="n" surfaceScale="2.2" lighting-color="#fff" result="l"><feDistantLight azimuth="235" elevation="52"/></feDiffuseLighting>
<feComposite in="l" in2="SourceGraphic" operator="in"/></filter>
<filter id="fe" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="5"/><feColorMatrix values="0 0 0 0 .55  0 0 0 0 .45  0 0 0 0 .35  0 0 0 .5 0"/><feComposite in2="SourceGraphic" operator="in"/></filter>
<filter id="ps" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="7" stdDeviation="9" flood-color="#000" flood-opacity=".55"/></filter>
<filter id="db" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation=".35"/></filter>
</defs>
<g clip-path="url(#lc)">
<rect width="512" height="512" fill="#3A2C22"/><rect width="512" height="512" fill="#fff" filter="url(#fe)" opacity=".55"/>
<circle cx="256" cy="256" r="206" fill="url(#lp)" filter="url(#ps)"/>
<circle cx="256" cy="256" r="206" fill="#fff" filter="url(#lg)" opacity=".28" style="mix-blend-mode:multiply"/>
<circle cx="256" cy="256" r="203" fill="none" stroke="#3E1E0C" stroke-width="5" opacity=".55"/>
<circle cx="256" cy="256" r="181" fill="none" stroke="#2A1408" stroke-width="2" opacity=".35" transform="translate(0 1.5)"/>
<circle cx="256" cy="256" r="181" fill="none" stroke="#24120A" stroke-width="5.5" stroke-dasharray="15 8" stroke-linecap="round"/>
<circle cx="256" cy="256" r="181" fill="none" stroke="#6B4027" stroke-width="1.4" stroke-dasharray="9 14" stroke-dashoffset="-3" stroke-linecap="round" transform="translate(-.6 -1.2)" opacity=".8"/>
<g transform="{T}" filter="url(#db)">
{path('#C58A55', 9.8, 'transform="translate(0 .55)" opacity=".5"')}
{path('#1E0D04', 9.8, 'transform="translate(0 -.35)" opacity=".9"')}
{path('#3A1A09', 9.2)}
</g></g>''')

# ASCII: neon volt characters on black, the w drawn in denser glyphs.
random.seed(4)
def on_mark(x, y):
    # sample distance to the w by walking the curve as a polyline (cheap approximation)
    return any(math.hypot(x - px, y - py) < 16 for px, py in PTS)
def bez(p0, p1, p2, p3, n=24):
    return [tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t * t * c + t ** 3 * d for a, b, c, d in zip(p0, p1, p2, p3)) for t in [i / n for i in range(n + 1)]]
segs = [((2.5, 44), (3, 64), (12, 80), (25, 80)), ((25, 80), (37, 80), (42, 64), (46, 50)), ((46, 50), (48.8, 40.5), (54.5, 31), (54, 22)), ((54, 22), (53.6, 13.5), (39, 12.5), (38.6, 22)),
        ((38.6, 22), (38.2, 31), (43.4, 40.5), (46.8, 50)), ((46.8, 50), (51, 64), (58, 80), (71, 80)), ((71, 80), (84, 80), (90, 56), (91.5, 26))]
PTS = [((x - 47) * 3.1 + 256, (y - 48) * 3.1 + 262) for s in segs for x, y in bez(*s)]
PTS += [(91.5 * 3.1 - 47 * 3.1 + 256, (y - 48) * 3.1 + 262) for y in range(14, 27, 2)]
glyphs = []
for gy in range(70, 452, 14):
    for gx in range(64, 452, 9):
        if math.hypot(gx - 256, gy - 256) > 230: continue
        hit = on_mark(gx, gy)
        if hit: glyphs.append(f'<text x="{gx}" y="{gy}" fill="#D7FF3A">{random.choice("@#%$W")}</text>')
        elif random.random() < .7: glyphs.append(f'<text x="{gx}" y="{gy}" fill="#D7FF3A" opacity="{random.choice([".22", ".3", ".4"])}">{random.choice(".:-=+*")}</text>')
ascii_ = svg(f'''<defs><clipPath id="ac"><rect width="512" height="512" rx="114"/></clipPath><radialGradient id="ag" cx="50%" cy="50%" r="50%"><stop offset="0" stop-color="#D7FF3A" stop-opacity=".14"/><stop offset="1" stop-color="#D7FF3A" stop-opacity="0"/></radialGradient></defs>
<g clip-path="url(#ac)"><rect width="512" height="512" fill="#050505"/><circle cx="256" cy="262" r="210" fill="url(#ag)"/>
<g font-family="Menlo, 'DejaVu Sans Mono', Consolas, monospace" font-size="14" font-weight="700" text-anchor="middle">{''.join(glyphs)}</g></g>''')

# Wool: a knitted ground and the w laid in two-ply yarn, with a soft halo of stray fibres.
wool = svg(f'''<defs>
<clipPath id="wc"><rect width="512" height="512" rx="114"/></clipPath>
<pattern id="knit" width="18" height="16" patternUnits="userSpaceOnUse"><rect width="18" height="16" fill="#6E2824"/>
<path d="M1 1Q4.5 9 9 15" fill="none" stroke="#8E3832" stroke-width="5" stroke-linecap="round"/><path d="M17 1Q13.5 9 9 15" fill="none" stroke="#81322D" stroke-width="5" stroke-linecap="round"/>
<path d="M2 2Q5 8 8.5 13" fill="none" stroke="#A9504A" stroke-width="1.2" stroke-linecap="round" opacity=".7"/><path d="M16 2Q13 8 9.5 13" fill="none" stroke="#9A443E" stroke-width="1.2" stroke-linecap="round" opacity=".5"/></pattern>
<filter id="fz" x="-10%" y="-10%" width="120%" height="120%"><feTurbulence type="fractalNoise" baseFrequency="1.1" numOctaves="2" seed="8"/><feDisplacementMap in="SourceGraphic" scale="3.2"/></filter>
<filter id="fb" x="-20%" y="-20%" width="140%" height="140%"><feTurbulence type="fractalNoise" baseFrequency="2.4" numOctaves="1" seed="2" result="t"/><feDisplacementMap in="SourceGraphic" in2="t" scale="7"/><feGaussianBlur stdDeviation=".4"/></filter>
<filter id="ws" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="2.4" stdDeviation="2.2" flood-color="#2A0A08" flood-opacity=".6"/></filter>
<filter id="kn" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".8" numOctaves="2" seed="9"/><feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 .22 0"/><feComposite in2="SourceGraphic" operator="in"/></filter>
</defs><g clip-path="url(#wc)">
<rect width="512" height="512" fill="url(#knit)"/><rect width="512" height="512" fill="#000" filter="url(#kn)"/>
<g transform="{T}">
<g filter="url(#ws)">
{path('#E8D3AA', 12, 'opacity=".22" filter="url(#fb)"')}
{path('#E9D5AE', 10.5, 'filter="url(#fz)"')}
{path('#A98C5C', 10.5, 'stroke-dasharray="1.3 1.5" stroke-linecap="butt" filter="url(#fz)"')}
{path('#FBF1DC', 5.6, 'stroke-dasharray="1.1 1.7" stroke-dashoffset="-1.4" stroke-linecap="butt" transform="translate(-.4 -.5)" filter="url(#fz)"')}
{path('#FFF8EA', 1.1, 'opacity=".55" transform="translate(-1.6 -1.9)" filter="url(#fb)"')}
</g></g></g>''')

for k, v in [('leather', leather), ('ascii', ascii_), ('wool', wool)]:
    open(f'img/logos/wl-icon-{k}.svg', 'w').write(v)
print('ok', len(glyphs))
