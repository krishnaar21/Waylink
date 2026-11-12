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

# 2 LINK — two chain links locked together, one over, one under.
g1, g2, bgl = '#0E7C6B', '#1FB592', '#E4F4EF'
L1 = '<rect x="70" y="200" width="216" height="116" rx="58" fill="none" stroke="{c}" stroke-width="{w}"/>'
L2 = '<rect x="226" y="200" width="216" height="116" rx="58" fill="none" stroke="{c}" stroke-width="{w}"/>'
I['wl-link'] = ('Link', tile(bgl,
    '<clipPath id="cx"><rect x="232" y="292" width="48" height="52"/></clipPath>'
    '<g transform="translate(256 258) scale(.86) rotate(-28) translate(-256 -258)">'
    + L1.format(c=g1, w=34) + L2.format(c=g2, w=34)
    + '<g clip-path="url(#cx)">' + L1.format(c=bgl, w=52) + L1.format(c=g1, w=34) + '</g></g>'))

# 3 SIGN — a diamond road sign with a turn-ahead arrow.
amb, ink = '#F2B33D', '#16150F'
I['wl-sign'] = ('Sign', tile('#1C1B19',
    f'<rect x="146" y="146" width="220" height="220" rx="40" transform="rotate(45 256 256)" fill="{amb}"/>'
    f'<path d="M224 352V286C224 250 246 228 282 228H302" fill="none" stroke="{ink}" stroke-width="34" stroke-linecap="round" stroke-linejoin="round"/>'
    f'<path d="M294 188L346 228L294 268Z" fill="{ink}" stroke="{ink}" stroke-width="10" stroke-linejoin="round"/>'))

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
