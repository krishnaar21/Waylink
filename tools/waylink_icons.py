# Waylink app icons: five directions, each one bold shape with a single idea.
# Writes img/logos/<key>.svg (512 tile, rx 114) and img/logos/png/<key>-<size>.png via render step.
D = '/home/claude/pulsarnav-web/img/logos/'

def tile(bg, body, defs=''):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs>{defs}</defs>'
            f'<rect width="512" height="512" rx="114" fill="{bg}"/>{body}</svg>')

I = {}
# 1 TURN — a W made of two turn arrows pointing forward, joined by U-bends.
c = '#4D6BF2'
w = ('M204 196L120 196L120 286'
     'M120 196L182.9 299.6A34 34 0 0 0 246 282L246 196L330 196'
     'M246 196L308.9 299.6A34 34 0 0 0 372 282L372 196')
I['wl-turn'] = ('Turn', tile('#EEF1FF',
    f'<g transform="translate(256 262) scale(1.22) translate(-256 -262) matrix(-1 0 0 1 502 6)"><path d="{w}" fill="none" stroke="{c}" stroke-width="40" stroke-linejoin="miter" stroke-miterlimit="4" stroke-linecap="butt"/></g>'))

# 2 LOOP — one unbroken line: a W whose middle peak loops back on itself, leaving as an arrow.
blue = '#1F4FD6'
loop = 'M112 170L170 336Q178 356 188 336L246 214C262 182 300 142 278 118C258 98 214 112 222 158C228 196 262 214 274 240L320 336Q328 356 338 336L392 214'
I['wl-loop'] = ('Loop', tile('#F2F1EC',
    f'<path d="{loop}" fill="none" stroke="{blue}" stroke-width="36" stroke-linecap="round" stroke-linejoin="round"/>'
    f'<path d="M356 226L394 202L410 246" fill="none" stroke="{blue}" stroke-width="36" stroke-linecap="round" stroke-linejoin="round"/>'))

# 3 GLOBE — a dark disc split by a winding road, centre line dashed, light on the cut edges.
g = '#7BDD80'
road = 'M332 70C300 170 200 190 236 270S216 380 176 444'
I['wl-split'] = ('Globe', tile(g,
    f'<mask id="m"><rect width="512" height="512" fill="#fff"/><path d="{road}" fill="none" stroke="#000" stroke-width="44"/></mask>'
    '<g mask="url(#m)"><circle cx="256" cy="256" r="168" fill="#0E0F0E"/>'
    f'<path d="{road}" fill="none" stroke="url(#gl)" stroke-width="110" opacity=".75"/></g>'
    f'<path d="{road}" fill="none" stroke="#0E0F0E" stroke-width="5" stroke-dasharray="14 14" stroke-linecap="round" clip-path="url(#cc)"/>',
    defs=f'<clipPath id="cc"><circle cx="256" cy="256" r="150"/></clipPath>'
         f'<linearGradient id="gl" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{g}" stop-opacity="0"/><stop offset=".5" stop-color="{g}" stop-opacity=".9"/><stop offset="1" stop-color="{g}" stop-opacity="0"/></linearGradient>'))

# 4 SWIFT — a heavy italic W with flat, type-like ends, already moving.
I['wl-swift'] = ('Swift', tile('#ECE8E1',
    '<clipPath id="cl"><rect x="60" y="160" width="400" height="196"/></clipPath>'
    '<g clip-path="url(#cl)"><g transform="translate(256 258) skewX(-12) translate(-256 -258)">'
    '<path d="M118 100L188 370L256 236L324 370L394 100" fill="none" stroke="#111" stroke-width="62" stroke-linejoin="miter" stroke-miterlimit="4"/></g></g>'))

# 5 HELMET — a full-face helmet leaning into the wind, one shape with a visor slit and chin vents.
red = '#E04A3A'
I['wl-visor'] = ('Helmet', tile('#FBEDEA',
    '<g transform="rotate(-8 256 270)">'
    '<mask id="m"><rect x="-100" y="-100" width="712" height="712" fill="#fff"/>'
    '<path d="M262 220H460V270H284A22 22 0 0 1 262 248Z" fill="#000"/>'
    '<rect x="318" y="320" width="48" height="12" rx="6" fill="#000"/><rect x="318" y="344" width="34" height="12" rx="6" fill="#000"/></mask>'
    f'<path mask="url(#m)" d="M112 296C112 178 192 112 290 112C362 112 410 162 410 232V342C410 360 398 372 380 372L336 400H168C136 400 112 376 112 344Z" fill="{red}"/>'
    '<path d="M118 304C150 300 200 306 236 330" fill="none" stroke="#FBEDEA" stroke-width="10" stroke-linecap="round" opacity=".9"/></g>'))

for k, (nm, svg) in I.items():
    open(D + k + '.svg', 'w').write(svg)
print([(k, v[0]) for k, v in I.items()])
