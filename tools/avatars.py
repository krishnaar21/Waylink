# Rider avatars: soft pastel illustrations with paper grain. 100x100 viewBox, round tile.
SKIN = ['#E2B48C', '#C98F64', '#A86E48', '#8A5636']
def grain(i): return f'<filter id="g{i}" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="{i}"/><feColorMatrix values="0 0 0 0 .3  0 0 0 0 .25  0 0 0 0 .2  0 0 0 .35 -.05"/><feComposite in2="SourceGraphic" operator="in"/></filter>'
def head(skin): return f'<path d="M30 92c2-14 10-20 20-20s18 6 20 20" fill="var(--shirt)"/><rect x="44" y="60" width="12" height="14" rx="5" fill="{skin}"/><ellipse cx="50" cy="48" rx="17" ry="19" fill="{skin}"/><circle cx="43.5" cy="49" r="1.9" fill="#2B2620"/><circle cx="56.5" cy="49" r="1.9" fill="#2B2620"/><path d="M46 57q4 3 8 0" stroke="#7A4A3A" stroke-width="1.8" fill="none" stroke-linecap="round"/><ellipse cx="39" cy="54" rx="3" ry="2" fill="#F29C8C" opacity=".35"/><ellipse cx="61" cy="54" rx="3" ry="2" fill="#F29C8C" opacity=".35"/>'
PARTS = {
 'helmet': lambda c, v: f'<path d="M30 52c0-16 9-27 20-27s20 11 20 27v8H30z" fill="{c}"/><path d="M33 44h34v9c0 3-2 4-4 4H37c-2 0-4-1-4-4z" fill="{v}"/><path d="M36 46h10" stroke="#fff" stroke-width="2" opacity=".5" stroke-linecap="round"/>',
 'open': lambda c, v: f'<path d="M31 46c0-13 8-22 19-22s19 9 19 22v4h-4v-4c0-9-6-15-15-15s-15 6-15 15v12h-4z" fill="{c}"/><rect x="38" y="43" width="24" height="8" rx="4" fill="{v}" opacity=".9"/><circle cx="44" cy="47" r="3.2" fill="#BFE3F2"/><circle cx="56" cy="47" r="3.2" fill="#BFE3F2"/>',
 'cap': lambda c, v: f'<path d="M32 42c0-10 8-16 18-16s18 6 18 16z" fill="{c}"/><path d="M50 40h26c0 3-3 5-6 5H50z" fill="{v}"/>',
 'hair': lambda c, v: f'<path d="M32 46c-1-14 8-21 18-21s19 6 18 21c-3-6-9-9-18-9s-15 3-18 9z" fill="{c}"/>',
 'hat': lambda c, v: f'<ellipse cx="50" cy="35" rx="26" ry="5" fill="{c}"/><path d="M38 34c0-9 5-13 12-13s12 4 12 13z" fill="{c}"/><rect x="38" y="30" width="24" height="3" fill="{v}"/>',
 'beard': lambda c, v: f'<path d="M34 52c2 12 8 17 16 17s14-5 16-17c-4 5-10 6-16 6s-12-1-16-6z" fill="{c}"/>',
 'bandana': lambda c, v: f'<path d="M33 53c4 8 10 11 17 11s13-3 17-11v9c-4 4-10 6-17 6s-13-2-17-6z" fill="{c}"/>',
 'mous': lambda c, v: f'<path d="M42 55.5q4-3 8-1q4-2 8 1q-4 2-8 0.5q-4 1.5-8-0.5z" fill="{c}"/>',
 'bindi': lambda c, v: f'<circle cx="50" cy="40.5" r="1.6" fill="{c}"/>',
 'long': lambda c, v: f'<path d="M31 50c-2-17 8-25 19-25s21 8 19 25c1 10 1 22-3 28h-6c3-8 4-18 3-26-4-6-8-8-13-8s-9 2-13 8c-1 8 0 18 3 26h-6c-4-6-4-18-3-28z" fill="{c}"/>',
 'turban': lambda c, v: f'<path d="M30 44c0-14 9-21 20-21s20 7 20 21c-5-3-12-5-20-5s-15 2-20 5z" fill="{c}"/><path d="M33 40c6-6 14-10 22-12M36 34c6-4 13-6 20-6" stroke="{v}" stroke-width="2" fill="none" opacity=".55" stroke-linecap="round"/><path d="M47 30l3 8 3-8" fill="{v}" opacity=".5"/>',
 'shades': lambda c, v: f'<rect x="37" y="45" width="11" height="7" rx="3" fill="#2B2620"/><rect x="52" y="45" width="11" height="7" rx="3" fill="#2B2620"/><path d="M48 48h4" stroke="#2B2620" stroke-width="2"/>',
}
A = [
 ('#C9D8F5', '#9DB6E8', 1, '#7C93C9', [('hair', '#1E1A18', ''), ('mous', '#1E1A18', ''), ('helmet', '#F08A8A', '#3A3F55')]),
 ('#F9E3C2', '#F1C98E', 0, '#D98AA0', [('long', '#1E1A18', ''), ('bindi', '#C2304A', ''), ('open', '#8E9BB0', '#5B6478')]),
 ('#CDEBDD', '#A3D8C0', 2, '#7FA9E0', [('hair', '#1E1A18', ''), ('mous', '#1E1A18', ''), ('cap', '#7FA9E0', '#5C84C2')]),
 ('#E2D7F6', '#C6B3EE', 1, '#4A4458', [('turban', '#F2A65A', '#B8692A'), ('beard', '#1E1A18', ''), ('shades', '', '')]),
 ('#FAD9C9', '#F3B79E', 0, '#9DB6E8', [('long', '#241C18', ''), ('bindi', '#C2304A', ''), ('bandana', '#E08A7A', '')]),
 ('#CFE6F5', '#A6CDEB', 3, '#5B6478', [('hair', '#1E1A18', ''), ('mous', '#1E1A18', ''), ('open', '#F2C94C', '#3A3F55')]),
 ('#F7D5DD', '#EFB1C0', 2, '#E08A7A', [('hair', '#1E1A18', ''), ('beard', '#2A221E', ''), ('bandana', '#6C8FD6', '')]),
 ('#E6EFC9', '#CCE09A', 1, '#F2A65A', [('long', '#1E1A18', ''), ('bindi', '#7A3BA0', ''), ('cap', '#F2A65A', '#D5843A')]),
 ('#D3EEEB', '#A8DCD6', 0, '#4A4458', [('hair', '#1E1A18', ''), ('helmet', '#F7F7F2', '#6C8FD6')]),
 ('#F5DFC8', '#EBC29C', 2, '#3E3A40', [('turban', '#6C8FD6', '#3E5A9A'), ('beard', '#1E1A18', '')]),
 ('#D8DCF7', '#B5BCEE', 3, '#E6DDC8', [('hair', '#9A9A9A', ''), ('mous', '#BDBDBD', ''), ('hat', '#3E3A40', '#8A7F70')]),
 ('#FBE7B5', '#F5D27A', 1, '#6C8FD6', [('long', '#1E1A18', ''), ('open', '#E5484D', '#2E3550')]),
]
order = ['long', 'hair', 'beard', 'mous', 'bandana', 'bindi', 'shades', 'turban', 'open', 'cap', 'hat', 'helmet']
for i, (b0, b1, sk, shirt, parts) in enumerate(A):
    skin = SKIN[sk]
    body = head(skin).replace('var(--shirt)', shirt)
    for name in order:
        for n, c, v in parts:
            if n == name: body += PARTS[n](c, v)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="b{i}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{b0}"/><stop offset="1" stop-color="{b1}"/></linearGradient>'
           f'<clipPath id="c{i}"><circle cx="50" cy="50" r="50"/></clipPath>{grain(i + 1)}</defs><g clip-path="url(#c{i})"><rect width="100" height="100" fill="url(#b{i})"/>'
           f'<circle cx="78" cy="22" r="30" fill="#fff" opacity=".18"/><g transform="translate(50 62) scale(1.14) translate(-50 -58)">{body}</g><rect width="100" height="100" fill="#fff" filter="url(#g{i + 1})"/></g></svg>')
    open(f'img/avatars/a{i + 1}.svg', 'w').write(svg)
print(len(A))
