# Rider avatars, Notion style: white faces, bold black ink, solid black hair, on soft pastel discs.
# Faces from across India: Sikh, Muslim, Hindu, Christian, Parsi, an elder, a rider; three women.
K = '#111'
INK = f'stroke="{K}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"'
def face(expr='calm'):
    o = f'<path d="M24 52q-6 1-5 8t8 5" fill="#fff" {INK}/><path d="M76 52q6 1 5 8t-8 5" fill="#fff" {INK}/>'
    o += f'<path d="M27 44c0-15 10-24 23-24s23 9 23 24v12c0 15-10 26-23 26s-23-11-23-26z" fill="#fff" {INK}/>'
    o += f'<path d="M49 53l-3 8h5" fill="none" {INK}/>'
    if expr == 'smile': o += f'<path d="M35 49q4-4 8 0M57 49q4-4 8 0" fill="none" {INK}/><path d="M42 68q8 6 16 0" fill="none" {INK}/>'
    else: o += f'<circle cx="40" cy="49" r="2.4" fill="{K}"/><circle cx="60" cy="49" r="2.4" fill="{K}"/><path d="M46 70h7" {INK}/>'
    o += f'<path d="M35 41q5-2 9 0M56 41q5-2 9 0" fill="none" {INK}/><path d="M32 60l2-3M35 61l2-3M64 60l2-3M67 61l2-3" stroke="{K}" stroke-width="1.6" stroke-linecap="round"/>'
    return o
P = {
 'quiff': f'<path d="M25 46c-3-16 6-28 22-30 10-2 22 2 27 12 3 6 2 14-1 20-2-9-8-15-16-17 3 4 4 9 3 13-8-6-20-8-35 2z" fill="{K}"/>',
 'side': f'<path d="M26 44c-1-16 10-25 25-25 13 0 22 8 23 21-5-6-12-9-20-9-2 4-9 8-28 13z" fill="{K}"/>',
 'curly': ''.join(f'<circle cx="{x}" cy="{y}" r="7" fill="{K}"/>' for x, y in [(30, 34), (38, 26), (48, 22), (58, 23), (67, 29), (72, 38), (28, 44)]) + f'<path d="M30 40q20-10 40 0" fill="{K}"/>',
 'grey': f'<path d="M26 44c-1-16 10-25 25-25 13 0 22 8 23 21-5-6-12-9-20-9-2 4-9 8-28 13z" fill="#fff" {INK}/><path d="M34 32q8-5 16-4M52 27q8 0 14 5" fill="none" stroke="{K}" stroke-width="1.6"/>',
 'beard': f'<path d="M27 58c2 16 11 26 23 26s21-10 23-26c-4 4-8 6-11 5-4-4-8-4-12-4s-8 0-12 4c-3 1-7-1-11-5z" fill="{K}"/><path d="M44 69q6 3 12 0" stroke="#fff" stroke-width="2.2" fill="none" stroke-linecap="round"/>',
 'mous': f'<path d="M40 64q10-6 20 0q-5 3-10 1q-5 2-10-1z" fill="{K}"/>',
 'turban': f'<path d="M23 46c0-18 12-29 27-29s27 11 27 29c-6-4-15-7-27-7s-21 3-27 7z" fill="#fff" {INK}/><path d="M29 37c8-8 18-12 30-12M31 30c8-5 16-7 26-7M50 17l-6 21" fill="none" stroke="{K}" stroke-width="2"/>',
 'cap': f'<path d="M27 37c0-10 10-16 23-16s23 6 23 16z" fill="#fff" {INK}/><path d="M33 31h34M38 26h24" stroke="{K}" stroke-width="1.4" stroke-dasharray="2 3"/>',
 'tilak': f'<path d="M50 30v8" stroke="{K}" stroke-width="3" stroke-linecap="round"/>',
 'glasses': f'<rect x="31" y="43" width="16" height="11" rx="2.5" fill="none" {INK}/><rect x="53" y="43" width="16" height="11" rx="2.5" fill="none" {INK}/><path d="M47 47h6" {INK}/>',
 'round': f'<circle cx="40" cy="49" r="7" fill="none" {INK}/><circle cx="60" cy="49" r="7" fill="none" {INK}/><path d="M47 48h6" {INK}/>',
 'cross': f'<path d="M50 88v7M47 91h6" stroke="{K}" stroke-width="2" stroke-linecap="round"/>',
 'parsi': f'<path d="M26 40c0-13 10-20 24-20s24 7 24 20c-7-3-15-4-24-4s-17 1-24 4z" fill="{K}"/>',
 'longf': f'<path d="M23 50c-3-20 10-32 27-32s30 12 27 32c1 14 0 26-5 34H28c-5-8-6-20-5-34z" fill="{K}"/>',
 'fringe': f'<path d="M27 46c0-16 10-26 23-26s23 10 23 26c-5-9-13-14-22-13-4 5-11 9-24 13z" fill="{K}"/>',
 'bun': f'<circle cx="50" cy="16" r="9" fill="{K}"/>',
 'hijab': f'<path d="M18 62c0-28 14-44 32-44s32 16 32 44v38H18z" fill="#fff" {INK}/><path d="M32 30q18-10 36 0" fill="none" stroke="{K}" stroke-width="1.6"/>',
 'bindi': f'<circle cx="50" cy="37" r="2.2" fill="{K}"/>',
 'earring': f'<circle cx="23" cy="68" r="2.6" fill="none" {INK}/><circle cx="77" cy="68" r="2.6" fill="none" {INK}/>',
 'helmet': f'<path d="M22 50c0-20 12-32 28-32s28 12 28 32v8H66v-8c0-10-7-16-16-16s-16 6-16 16v8H22z" fill="#fff" {INK}/><path d="M50 18v14" stroke="{K}" stroke-width="5"/>',
}
SHIRT = f'<path d="M20 100c2-12 13-18 30-18s28 6 30 18" fill="#fff" {INK}/><path d="M44 82l6 7 6-7" fill="none" {INK}/>'
A = [  # disc colour, parts drawn in order
 ('#F6D9C7', ['shirt', 'face', 'quiff', 'glasses']),
 ('#D8E8D2', ['shirt', 'face:smile', 'beard', 'turban']),
 ('#D3E3F2', ['shirt', 'face', 'beard', 'cap']),
 ('#F3E1B9', ['shirt', 'face:smile', 'side', 'mous', 'tilak']),
 ('#E4DCF2', ['shirt', 'face', 'curly', 'cross']),
 ('#EFE3CF', ['shirt', 'face:smile', 'parsi', 'mous', 'round']),
 ('#D5ECEA', ['shirt', 'face', 'mous', 'helmet']),
 ('#EADFD6', ['shirt', 'face:smile', 'grey', 'mous', 'glasses']),
 ('#F6D3DC', ['longf', 'shirt', 'face:smile', 'fringe', 'bindi', 'earring']),
 ('#DCEBDF', ['hijab', 'shirt', 'face:smile']),
 ('#F7E7B4', ['bun', 'shirt', 'face', 'fringe', 'bindi', 'round']),
]
for i, (bg, parts) in enumerate(A):
    body = ''
    for p in parts:
        if p == 'shirt': body += SHIRT
        elif p.startswith('face'): body += face('smile' if ':smile' in p else 'calm')
        else: body += P[p]
    if 'hijab' in parts:  # keep the hijab's face opening tight: redraw the head edge over it
        body = body.replace('<path d="M27 44c0-15', '<path d="M27 44c0-15', 1)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><clipPath id="c{i}"><circle cx="50" cy="50" r="50"/></clipPath></defs>'
           f'<g clip-path="url(#c{i})"><rect width="100" height="100" fill="{bg}"/><g transform="translate(50 58) scale(1.04) translate(-50 -56)">{body}</g></g></svg>')
    open(f'img/avatars/a{i + 1}.svg', 'w').write(svg)
print(len(A))
