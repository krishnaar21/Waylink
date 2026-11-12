# Rider avatars: helmets and gear, front view, pastel with paper grain. 100x100, round tile.
def grain(i): return f'<filter id="g{i}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="{i}"/><feColorMatrix values="0 0 0 0 .3  0 0 0 0 .25  0 0 0 0 .2  0 0 0 .32 -.05"/><feComposite in2="SourceGraphic" operator="in"/></filter>'
SH = '<ellipse cx="50" cy="88" rx="26" ry="4" fill="#000" opacity=".1"/>'
def fullface(c, v, live=''):
    return (SH + f'<path d="M24 52c0-19 12-32 26-32s26 13 26 32v14c0 8-6 16-14 18H38c-8-2-14-10-14-18z" fill="{c}"/>'
            f'{live}<path d="M28 46c4-7 12-11 22-11s18 4 22 11v12c0 3-3 5-6 5H34c-3 0-6-2-6-5z" fill="{v}"/>'
            '<path d="M33 44c4-4 9-5 15-5" stroke="#fff" stroke-width="3" opacity=".55" stroke-linecap="round" fill="none"/>'
            '<path d="M40 74h20M42 79h16" stroke="#000" stroke-opacity=".18" stroke-width="2.2" stroke-linecap="round"/>'
            '<path d="M30 30c6-6 13-9 20-9" stroke="#fff" stroke-width="3" opacity=".35" stroke-linecap="round" fill="none"/>')
def openface(c, strap, lens, live=''):
    return (SH + f'<path d="M24 56c0-21 12-34 26-34s26 13 26 34v8H24z" fill="{c}"/>{live}'
            '<path d="M30 54c0-14 9-22 20-22s20 8 20 22v18c0 6-9 12-20 12s-20-6-20-12z" fill="#E7B08A"/>'
            f'<rect x="24" y="48" width="52" height="7" rx="3.5" fill="{strap}"/>'
            f'<circle cx="40" cy="52" r="8.5" fill="{strap}"/><circle cx="60" cy="52" r="8.5" fill="{strap}"/>'
            f'<circle cx="40" cy="52" r="6" fill="{lens}"/><circle cx="60" cy="52" r="6" fill="{lens}"/>'
            '<circle cx="38" cy="50" r="1.8" fill="#fff" opacity=".8"/><circle cx="58" cy="50" r="1.8" fill="#fff" opacity=".8"/>'
            '<path d="M42 70c3 3 13 3 16 0" stroke="#5A3B2E" stroke-width="3.4" stroke-linecap="round" fill="none"/>'
            '<path d="M30 30c6-6 13-9 20-9" stroke="#fff" stroke-width="3" opacity=".35" stroke-linecap="round" fill="none"/>')
def mx(c, peak, goggle, lens, live=''):
    return (SH + f'<path d="M24 50c0-18 12-30 26-30s26 12 26 30v16c0 4-2 8-6 10l-12 10H42L30 76c-4-2-6-6-6-10z" fill="{c}"/>{live}'
            f'<path d="M20 34c8-10 20-14 30-14s22 4 30 14l-6 6c-6-6-14-9-24-9s-18 3-24 9z" fill="{peak}"/>'
            f'<rect x="27" y="44" width="46" height="16" rx="8" fill="{goggle}"/><rect x="31" y="47" width="38" height="10" rx="5" fill="{lens}"/>'
            '<path d="M35 49h12" stroke="#fff" stroke-width="2" opacity=".6" stroke-linecap="round"/>'
            '<path d="M43 68l7 10 7-10z" fill="#000" opacity=".2"/><circle cx="44" cy="72" r="1.6" fill="#000" opacity=".25"/><circle cx="56" cy="72" r="1.6" fill="#000" opacity=".25"/>')
def stripe(col): return f'<path d="M46 21h8v28h-8z" fill="{col}"/>'
def twin(col): return f'<path d="M42 21h5v24h-5zM53 21h5v24h-5z" fill="{col}"/>'
def num(n, col): return f'<text x="50" y="35" text-anchor="middle" font-family="Arial Black, Arial, sans-serif" font-weight="900" font-size="12" fill="{col}">{n}</text>'
def star(col): return f'<path d="M50 24l2.6 5.4 5.9.8-4.3 4.1 1 5.8L50 37.4l-5.2 2.7 1-5.8-4.3-4.1 5.9-.8z" fill="{col}"/>'
def bolt(col): return f'<path d="M53 22l-9 13h6l-3 10 10-14h-6z" fill="{col}"/>'
def check(col): return ''.join(f'<rect x="{38 + (k % 4) * 6}" y="{24 + (k // 4) * 6}" width="6" height="6" fill="{col}"/>' for k in range(8) if (k + k // 4) % 2 == 0)
A = [
 ('#C9D8F5', '#9DB6E8', fullface('#F08A8A', '#3A3F55', stripe('#FFF3E0'))),
 ('#F9E3C2', '#F1C98E', openface('#8FB8A6', '#5B4636', '#9FD3EA')),
 ('#CDEBDD', '#A3D8C0', mx('#F7F2E8', '#F2A65A', '#3A3F55', '#F6C76A', num('7', '#F2A65A'))),
 ('#E2D7F6', '#C6B3EE', fullface('#2F3448', '#A9B9F0', twin('#E5484D'))),
 ('#FAD9C9', '#F3B79E', openface('#F2E6CF', '#7A4E35', '#F7B267', star('#E5484D'))),
 ('#CFE6F5', '#A6CDEB', mx('#6C8FD6', '#F7F2E8', '#2E3550', '#9FE3D6', bolt('#F2C94C'))),
 ('#F7D5DD', '#EFB1C0', fullface('#F7F2E8', '#E8798F', check('#2F3448'))),
 ('#E6EFC9', '#CCE09A', openface('#C2452B', '#2F3448', '#B5E0F5', stripe('#F7F2E8'))),
 ('#D3EEEB', '#A8DCD6', mx('#F2C94C', '#2F3448', '#2F3448', '#F08A8A', num('46', '#2F3448'))),
 ('#F5DFC8', '#EBC29C', fullface('#9A7356', '#F6D6A8', twin('#F7F2E8'))),
 ('#D8DCF7', '#B5BCEE', openface('#3E3A40', '#C9A27A', '#C6B3EE', bolt('#F2C94C'))),
 ('#FBE7B5', '#F5D27A', fullface('#7FB7A8', '#2E3550', star('#F7F2E8'))),
]
for i, (b0, b1, body) in enumerate(A):
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="b{i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{b0}"/><stop offset="1" stop-color="{b1}"/></linearGradient>'
           f'<clipPath id="c{i}"><circle cx="50" cy="50" r="50"/></clipPath>{grain(i + 1)}</defs><g clip-path="url(#c{i})"><rect width="100" height="100" fill="url(#b{i})"/>'
           f'<circle cx="80" cy="18" r="28" fill="#fff" opacity=".2"/><g transform="translate(50 56) scale(1.18) translate(-50 -54)">{body}</g><rect width="100" height="100" fill="#fff" filter="url(#g{i + 1})"/></g></svg>')
    open(f'img/avatars/a{i + 1}.svg', 'w').write(svg)
print(len(A))
