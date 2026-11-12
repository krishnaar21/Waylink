# Rider avatars in a clean line style: white three-quarter faces, black hair, fine ink.
# Faces turn slightly right; one ear shows on the left. Indian looks through hair and accessories.
K = '#111'
L = f'stroke="{K}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"'
F = f'fill="#fff" {L}'
HEAD = (f'<path d="M45 76v10h12v-7" {F}/>'
        f'<path d="M31 50c0-15 10-25 23-25s21 10 21 24c0 6-1 11-2 15-2 9-9 16-18 16-13 0-24-12-24-30z" {F}/>'
        f'<path d="M33 52c-6-2-9 2-8 6s4 7 9 5" {F}/><path d="M29 56q2-1 3 1" fill="none" stroke="{K}" stroke-width="1.6"/>')
def face(mouth='smile', eyes='dot'):
    e = (f'<circle cx="51" cy="52" r="2" fill="{K}"/><circle cx="64" cy="52" r="2" fill="{K}"/>' if eyes == 'dot'
         else f'<path d="M48.5 52q2.5-2.5 5 0M61.5 52q2.5-2.5 5 0" fill="none" {L}/>')
    m = {'smile': f'<path d="M54 66q4 2.5 8 0" fill="none" {L}/>', 'open': f'<path d="M53 65q5 5 10 0z" fill="#fff" {L}/>',
         'flat': f'<path d="M55 67h6" {L}/>'}[mouth]
    return e + f'<path d="M66 54q3 4 0 6" fill="none" {L}/>' + m
SHIRT = {
 'tee': f'<path d="M20 100c3-10 13-15 30-15s28 5 31 15" {F}/><path d="M43 85q8 5 16 0" fill="none" {L}/>',
 'kurta': f'<path d="M20 100c3-10 13-15 30-15s28 5 31 15" {F}/><path d="M44 85h14v4q-7 4-14 0z" fill="#fff" {L}/><path d="M51 89v11" {L}/>',
 'jacket': f'<path d="M20 100c3-10 13-15 30-15s28 5 31 15" fill="{K}"/><path d="M45 86l6 8 6-8" fill="#fff" {L}/>',
}
HAIR = {
 'short': f'<path d="M30 50c-3-17 8-28 24-28 14 0 23 9 22 22-4-6-10-9-16-9-6 5-16 8-26 15z" fill="{K}"/>',
 'swoop': f'<path d="M30 52c-4-18 8-30 25-30 13 0 22 8 22 19l-6 1c-4-5-11-6-17-5 2 3 2 6 0 9-8-3-16 0-24 6z" fill="{K}"/>',
 'spiky': f'<path d="M30 50c-3-14 4-25 16-28l2-5 5 4 4-5 3 6 5-3v6c7 3 11 10 11 19-5-7-12-10-20-10-9 3-19 8-26 16z" fill="{K}"/>',
 'bob': f'<path d="M28 76c-6-10-6-26 0-37 5-10 14-16 26-16 13 0 22 10 22 22-6-4-12-6-18-5-6 3-12 4-19 3 0 12 1 22-3 33z" fill="{K}"/>',
 'bobfront': f'<path d="M73 44v26c4-6 5-16 3-26z" fill="{K}"/>',
 'long': f'<path d="M26 92c-5-18-5-38 1-52 6-12 15-18 27-18 14 0 23 10 23 23-7-5-13-7-20-6-7 3-13 4-20 3-1 18 0 34-11 50z" fill="{K}"/>',
 'bun': f'<circle cx="44" cy="19" r="8" fill="{K}"/><path d="M30 50c-3-16 8-27 24-27 13 0 22 9 22 21-5-6-11-8-18-8-8 2-17 6-28 14z" fill="{K}"/>',
 'curly': ''.join(f'<circle cx="{x}" cy="{y}" r="6" fill="{K}"/>' for x, y in [(32, 44), (33, 35), (39, 27), (48, 23), (58, 23), (67, 27), (73, 35)]) + f'<path d="M32 46q12-11 26-11 10 0 16 6v-8H32z" fill="{K}"/>',
}
TOP = {
 'capback': f'<path d="M30 44c0-13 10-21 24-21s22 8 22 20z" fill="#fff" {L}/><path d="M30 44c-7 0-13 3-15 7l15-1z" fill="#fff" {L}/><path d="M60 26q4 4 3 9" fill="none" {L}/><path d="M44 42h20" stroke="{K}" stroke-width="1.6"/>',
 'cap': f'<path d="M31 43c0-12 10-20 23-20s22 8 22 20z" fill="{K}"/><path d="M54 43h30c-1 3-4 5-8 5H54z" fill="{K}"/>',
 'turban': f'<path d="M28 48c0-18 11-28 26-28s24 10 24 27c-6-4-14-7-24-7s-19 3-26 8z" fill="#fff" {L}/><path d="M32 38c8-8 18-12 30-12M36 31c8-5 16-6 24-5M54 21l-7 18" fill="none" stroke="{K}" stroke-width="1.6"/>',
 'band': f'<path d="M31 40q22-8 44-1v6q-22-7-44 1z" fill="#fff" {L}/>',
 'hijab': f'<path d="M24 100V62c0-26 13-42 30-42s28 14 28 38v42z" fill="#fff" {L}/>',
}
ACC = {
 'glasses': f'<circle cx="51" cy="52" r="5.5" fill="none" {L}/><circle cx="65" cy="52" r="5.5" fill="none" {L}/><path d="M56.5 52h3M45.5 51l-10-2" {L}/>',
 'square': f'<rect x="45" y="47" width="11" height="9" rx="2.5" fill="none" {L}/><rect x="59" y="47" width="11" height="9" rx="2.5" fill="none" {L}/><path d="M56 51h3M45 50l-10-2" {L}/>',
 'beard': f'<path d="M34 63c3 9 10 15 20 15 8 0 14-5 17-12-3 2-6 2-9 0-4-2-8-2-11 0-5 1-10 0-17-3z" fill="{K}"/><path d="M54 68q4 2 8 0" stroke="#fff" stroke-width="1.8" fill="none" stroke-linecap="round"/>',
 'bindi': f'<circle cx="57.5" cy="44" r="1.6" fill="{K}"/>',
 'earring': f'<circle cx="29" cy="66" r="2.2" fill="none" {L}/>',
 'stud': f'<circle cx="29" cy="64" r="1.6" fill="{K}"/>',
}
A = [  # disc, shirt, hair (behind), top, accessories, mouth, eyes
 ('#F6E3D6', 'tee',    'swoop', None,      ['glasses'],          'smile', 'dot'),
 ('#E3EEDF', 'kurta',  None,    'turban',  ['beard'],            'smile', 'happy'),
 ('#DDE8F4', 'tee',    'short', 'capback', [],                   'open',  'dot'),
 ('#F4EBD3', 'jacket', 'spiky', None,      ['stud'],             'smile', 'dot'),
 ('#ECE4F4', 'kurta',  'curly', None,      ['square'],           'flat',  'dot'),
 ('#E6EFEE', 'jacket', 'short', 'cap',     [],                   'smile', 'happy'),
 ('#F1E6DE', 'tee',    'swoop', 'band',    ['glasses'],          'open',  'dot'),
 ('#F7DFE5', 'kurta',  'long',  None,      ['bindi', 'earring'], 'smile', 'happy'),
 ('#E2EEE4', 'tee',    None,    'hijab',   ['glasses'],          'smile', 'dot'),
 ('#F7EDCF', 'jacket', 'bun',   None,      ['bindi', 'square'],  'smile', 'dot'),
 ('#F8E4D4', 'tee',    'bob',   None,      ['earring'],          'open',  'happy'),
]
for i, (bg, shirt, hair, top, acc, mo, ey) in enumerate(A):
    b = ''
    if hair in ('bob', 'long'): b += HAIR[hair]
    b += SHIRT[shirt]
    if top == 'hijab': b += TOP['hijab']
    b += HEAD + face(mo, ey)
    for a in acc:
        if a == 'beard': b += ACC[a]
    if hair and hair not in ('bob', 'long'): b += HAIR[hair]
    if hair == 'bob': b += HAIR['bobfront'] + f'<path d="M30 46c3-12 11-19 22-19 10 0 18 6 21 15-7-4-15-6-23-5-8 3-14 6-20 9z" fill="{K}"/>'
    if hair == 'long': b += f'<path d="M30 48c2-14 11-21 23-21 11 0 19 7 22 16-8-5-15-7-23-6-8 3-15 6-22 11z" fill="{K}"/>'
    if top and top != 'hijab': b += TOP[top]
    for a in acc:
        if a != 'beard': b += ACC[a]
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><clipPath id="c{i}"><circle cx="50" cy="50" r="50"/></clipPath></defs>'
           f'<g clip-path="url(#c{i})"><rect width="100" height="100" fill="{bg}"/><g transform="translate(50 58) scale(1.06) translate(-52 -56)">{b}</g></g></svg>')
    open(f'img/avatars/a{i + 1}.svg', 'w').write(svg)
print(len(A))
