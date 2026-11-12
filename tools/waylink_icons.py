# Waylink app icons: five directions, each one bold shape with a single idea.
# Writes img/logos/<key>.svg (512 tile, rx 114) and img/logos/png/<key>-<size>.png via render step.
D = 'img/logos/'  # run from the repo root

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

# 2 LINK — a hollow W drawn as one continuous tube, a node at each end: two points, linked.
teal, mint = '#0F2E2A', '#5EE6B8'
wpath = 'M120 178L178 340L256 222L334 340L392 178'
I['wl-link'] = ('Link', tile(teal,
    f'<path d="{wpath}" fill="none" stroke="{mint}" stroke-width="64" stroke-linecap="round" stroke-linejoin="round"/>'
    f'<path d="{wpath}" fill="none" stroke="{teal}" stroke-width="28" stroke-linecap="round" stroke-linejoin="round"/>'
    f'<circle cx="120" cy="178" r="20" fill="{mint}"/><circle cx="392" cy="178" r="20" fill="{mint}"/>'))

# 3 SIGNPOST — a post with two direction plates: one way out, one way back.
post, amber, teal2 = '#1C1B19', '#F2B33D', '#2A9D8F'
I['wl-sign'] = ('Signpost', tile('#F3EFE6',
    f'<rect x="240" y="108" width="32" height="312" rx="16" fill="{post}"/>'
    f'<path d="M150 150H350L392 190L350 230H150A16 16 0 0 1 134 214V166A16 16 0 0 1 150 150Z" fill="{amber}"/>'
    f'<path d="M362 256H162L120 296L162 336H362A16 16 0 0 0 378 320V272A16 16 0 0 0 362 256Z" fill="{teal2}"/>'
    f'<rect x="180" y="408" width="152" height="24" rx="12" fill="{post}"/>'))

# 4 SWIFT — a heavy italic W with flat, type-like ends, already moving.
I['wl-swift'] = ('Swift', tile('#ECE8E1',
    '<clipPath id="cl"><rect x="60" y="160" width="400" height="196"/></clipPath>'
    '<g clip-path="url(#cl)"><g transform="translate(256 258) skewX(-12) translate(-256 -258)">'
    '<path d="M118 100L188 370L256 236L324 370L394 100" fill="none" stroke="#111" stroke-width="62" stroke-linejoin="miter" stroke-miterlimit="4"/></g></g>'))

# 5 BEACON — a map pin sending out a signal: where you are, linked to the bike.
blu = '#7AA2FF'
I['wl-beacon'] = ('Beacon', tile('#141821',
    f'<g transform="translate(14 22)"><path d="M226 128c-60 0-104 44-104 102 0 70 82 124 98 134a12 12 0 0 0 12 0c16-10 98-64 98-134 0-58-44-102-104-102z" fill="{blu}"/>'
    '<circle cx="226" cy="228" r="36" fill="#141821"/>'
    f'<path d="M318 132a78 78 0 0 1 32 56" fill="none" stroke="{blu}" stroke-width="20" stroke-linecap="round" opacity=".9"/>'
    f'<path d="M330 88a128 128 0 0 1 62 94" fill="none" stroke="{blu}" stroke-width="20" stroke-linecap="round" opacity=".5"/></g>'))

for k, (nm, svg) in I.items():
    open(D + k + '.svg', 'w').write(svg)
print([(k, v[0]) for k, v in I.items()])
