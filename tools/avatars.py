# Rider avatars, Notion style: white faces, bold ink, solid black hair, pastel discs.
# Young to middle-aged, mostly clean-shaven, each with their own look.
K = '#111'
L = f'stroke="{K}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"'
F = f'fill="#fff" {L}'
def head(sk='#fff'):
    FS = f'fill="{sk}" {L}'
    return (f'<path d="M44 76v8h12v-8" {FS}/>'                                          # neck
            f'<path d="M28 48c0-14 9-24 22-24s22 10 22 24v6c0 13-9 25-22 25s-22-12-22-25z" {FS}/>'  # face: soft jaw
            f'<path d="M28 52c-5-1-7 3-6 6s3 6 7 5M72 52c5-1 7 3 6 6s-3 6-7 5" {FS}/>')       # ears
def eyes(kind='dot'):
    if kind == 'smile': e = f'<path d="M37 51q3.5-3 7 0M56 51q3.5-3 7 0" fill="none" {L}/>'
    else: e = f'<ellipse cx="40.5" cy="51" rx="2.2" ry="2.6" fill="{K}"/><ellipse cx="59.5" cy="51" rx="2.2" ry="2.6" fill="{K}"/>'
    return e + f'<path d="M36 44q4.5-2 9 0M55 44q4.5-2 9 0" fill="none" {L}/>'
NOSE = f'<path d="M50 55q-2.5 5-.5 7.5h3" fill="none" {L}/>'
def mouth(kind='smile'):
    return {'smile': f'<path d="M44 68q6 4.5 12 0" fill="none" {L}/>', 'flat': f'<path d="M46 69h8" {L}/>',
            'grin': f'<path d="M43 67q7 7 14 0z" fill="#fff" {L}/>'}[kind]
BLUSH = f'<path d="M33 61l2-2.6M36 62l2-2.6M62 61l2-2.6M65 62l2-2.6" stroke="#7A3B2A" stroke-width="1.4" stroke-linecap="round"/>'
SHIRT = {
 'tee': f'<path d="M18 100c3-11 14-17 32-17s29 6 32 17" {F}/><path d="M42 83q8 6 16 0" fill="none" {L}/>',
 'jacket': f'<path d="M18 100c3-11 14-17 32-17s29 6 32 17" fill="{K}"/><path d="M44 84l6 9 6-9" fill="#fff" {L}/><path d="M50 93v7" stroke="#fff" stroke-width="1.6"/>',
 'kurta': f'<path d="M18 100c3-11 14-17 32-17s29 6 32 17" {F}/><path d="M42 84h16v4c-2 2-5 3-8 3s-6-1-8-3z" fill="#fff" {L}/><path d="M50 91v9" {L}/><circle cx="50" cy="94" r="1" fill="{K}"/><circle cx="50" cy="98" r="1" fill="{K}"/>',
 'hoodie': f'<path d="M18 100c3-11 14-17 32-17s29 6 32 17" {F}/><path d="M34 86q16 10 32 0" fill="none" {L}/><path d="M45 90v6M55 90v6" {L}/>',
}
HAIR_BACK = {
 'long': f'<path d="M24 50c-3-20 10-31 26-31s29 11 26 31c2 14 1 26-3 33H27c-4-7-5-19-3-33z" fill="{K}"/>',
 'longm': f'<path d="M25 50c-2-18 10-29 25-29s27 11 25 29c1 10 0 19-3 25H28c-3-6-4-15-3-25z" fill="{K}"/>',
 'bob': f'<path d="M24 52c-2-19 10-30 26-30s28 11 26 30c0 8-1 14-4 19H28c-3-5-4-11-4-19z" fill="{K}"/>',
 'bun': f'<circle cx="50" cy="15" r="8.5" fill="{K}"/>',
 'hijab': f'<path d="M17 66c0-30 14-46 33-46s33 16 33 46v34H17z" {F}/><path d="M30 34q20-12 40 0M25 52q25-8 50 0" fill="none" stroke="{K}" stroke-width="1.4"/>',
}
HAIR = {
 'short': f'<path d="M27 46c-2-15 8-25 23-25 13 0 22 7 23 19-1 4-3 6-5 7-2-7-6-11-12-12-6 4-15 6-29 11z" fill="{K}"/>',
 'quiff': f'<path d="M27 46c-3-14 5-26 20-28 6-1 13 0 18 4 6 4 9 11 8 18-1 3-3 5-5 6-1-6-5-10-11-11 2 3 2 6 1 9-7-5-17-7-31 2z" fill="{K}"/><path d="M48 24q8-1 14 4" stroke="#fff" stroke-width="1.4" fill="none"/>',
 'curly': ''.join(f'<circle cx="{x}" cy="{y}" r="6.6" fill="{K}"/>' for x, y in [(29, 40), (32, 31), (40, 24), (50, 21), (60, 23), (68, 30), (71, 39)]) + f'<path d="M30 42q20-12 40 0v-8H30z" fill="{K}"/>',
 'side': f'<path d="M27 46c-1-15 9-25 24-25 12 0 21 7 22 19-5-6-11-9-19-9-3 5-11 10-27 15z" fill="{K}"/>',
 'messy': f'<path d="M26 47c-3-16 7-27 23-27 15 0 25 9 25 23l-4-3-1 5-4-5-3 5-3-6-4 5-2-6-5 5-1-6-5 6-2-5-5 6z" fill="{K}"/>',
 'fringe': f'<path d="M28 47c0-15 9-25 22-25s22 10 22 25c-4-8-11-12-20-12-4 5-11 9-24 12z" fill="{K}"/>',
 'fringeb': f'<path d="M28 45c0-14 9-23 22-23s22 9 22 23c-6-5-14-7-22-7s-16 2-22 7z" fill="{K}"/>',
 'pony': f'<path d="M28 46c0-15 9-24 22-24s22 9 22 24c-6-6-13-9-22-9s-16 3-22 9z" fill="{K}"/><path d="M70 36c8 2 12 10 9 20-2-6-5-9-9-10z" fill="{K}"/>',
}
TOP = {
 'turban': f'<path d="M24 46c0-18 11-28 26-28s26 10 26 28c-6-4-15-7-26-7s-20 3-26 7z" {F}/><path d="M30 37c8-8 18-12 30-12M33 30c7-5 15-7 24-7M50 18l-5 21" fill="none" stroke="{K}" stroke-width="1.8"/>',
 'cap': f'<path d="M27 40c0-12 10-19 23-19s23 7 23 19z" fill="{K}"/><path d="M26 40c-6 0-11 2-13 5h14z" fill="{K}"/><circle cx="50" cy="21" r="2" fill="{K}"/>',
 'band': f'<path d="M27 38q23-9 46 0v6q-23-9-46 0z" fill="#fff" {L}/><path d="M73 40l8-2-3 7" fill="#fff" {L}/>',
 'beanie': f'<path d="M26 44c0-16 11-25 24-25s24 9 24 25z" {F}/><path d="M26 39h48v7H26z" fill="{K}"/><path d="M36 28v10M44 24v14M52 23v15M60 25v13" stroke="{K}" stroke-width="1.3"/><circle cx="50" cy="17" r="4" {F}/>',
 'helmet': f'<path d="M22 54c0-22 12-34 28-34s28 12 28 34v6h-8v-8c0-10-8-16-20-16s-20 6-20 16v8h-8z" {F}/><path d="M50 20v14" stroke="{K}" stroke-width="4"/>',
}
ACC = {
 'specs': f'<rect x="33" y="45" width="15" height="11" rx="3" fill="none" {L}/><rect x="52" y="45" width="15" height="11" rx="3" fill="none" {L}/><path d="M48 49h4" {L}/>',
 'round': f'<circle cx="40.5" cy="51" r="6.6" fill="none" {L}/><circle cx="59.5" cy="51" r="6.6" fill="none" {L}/><path d="M47 50h6" {L}/>',
 'shades': f'<path d="M33 46h15v5c0 4-3 6-7 6h-1c-4 0-7-2-7-6zM52 46h15v5c0 4-3 6-7 6h-1c-4 0-7-2-7-6z" fill="{K}"/><path d="M48 48h4" {L}/>',
 'beard': f'<path d="M28 57c1 14 10 23 22 23s21-9 22-23c-3 4-7 6-10 5-4-4-8-4-12-4s-8 0-12 4c-3 1-7-1-10-5z" fill="{K}"/><path d="M44 68q6 3 12 0" stroke="#fff" stroke-width="2" fill="none" stroke-linecap="round"/>',
 'stubble': ''.join(f'<circle cx="{x}" cy="{y}" r=".9" fill="{K}"/>' for x, y in [(38, 70), (42, 73), (47, 75), (53, 75), (58, 73), (62, 70), (35, 66), (65, 66), (44, 77), (56, 77)]),
 'bindi': f'<circle cx="50" cy="40" r="2" fill="{K}"/>',
 'earrings': f'<circle cx="23" cy="66" r="2.6" fill="none" {L}/><circle cx="77" cy="66" r="2.6" fill="none" {L}/>',
 'stud': f'<circle cx="24" cy="64" r="1.8" fill="{K}"/>',
 'phones': f'<path d="M30 86q20 10 40 0" fill="none" stroke="{K}" stroke-width="3.4"/><rect x="24" y="80" width="9" height="11" rx="4" fill="{K}"/><rect x="67" y="80" width="9" height="11" rx="4" fill="{K}"/>',
 'nosering': f'<circle cx="53" cy="62" r="2" fill="none" stroke="{K}" stroke-width="1.5"/>',
}
SK = ['#E3B48C', '#CF9A6E', '#B9825A', '#9C6A46']
A = [  # (disc, shirt, back hair, front hair, top, accessories, eyes, mouth, skin)
 ('#F6D9C7', 'kurta',  None,    'quiff',  None,     ['specs'],              'dot',   'smile', 1),
 ('#D8E8D2', 'jacket', None,    None,     'turban', ['beard'],              'smile', 'smile', 2),
 ('#D3E3F2', 'hoodie', None,    'short',  'cap',    ['phones'],             'dot',   'grin',  3),
 ('#F3E1B9', 'tee',    None,    'messy',  'band',   ['stud'],               'smile', 'grin',  1),
 ('#E4DCF2', 'jacket', 'longm', 'side',   None,     ['stubble'],            'dot',   'flat',  2),
 ('#EFE3CF', 'kurta',  None,    'curly',  None,     ['round'],              'smile', 'smile', 3),
 ('#EADFD6', 'hoodie', None,    None,     'beanie', ['shades'],             'dot',   'smile', 0),
 ('#F6D3DC', 'kurta',  'long',  'fringe', None,     ['bindi', 'earrings'],  'smile', 'smile', 1),
 ('#DCEBDF', 'hoodie', 'hijab', None,     None,     [],                     'smile', 'grin',  2),
 ('#F7E7B4', 'jacket', 'bun',   'fringeb',None,     ['specs', 'nosering', 'bindi'], 'dot', 'smile', 0),
 ('#F9DCC4', 'tee',    'bob',   'pony',   'band',   ['earrings'],           'smile', 'grin',  3),
]
for i, (bg, shirt, back, front, top, acc, ey, mo, sk) in enumerate(A):
    b = (HAIR_BACK[back] if back and back != 'hijab' else '') + SHIRT[shirt]
    if back == 'hijab': b = SHIRT[shirt] + HAIR_BACK['hijab']
    b += head(SK[sk]) + eyes(ey) + NOSE + mouth(mo) + BLUSH
    for a in acc:
        if a in ('beard', 'stubble'): b += ACC[a]
    if front: b += HAIR[front]
    if top: b += TOP[top]
    for a in acc:
        if a not in ('beard', 'stubble'): b += ACC[a]
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><clipPath id="c{i}"><circle cx="50" cy="50" r="50"/></clipPath></defs>'
           f'<g clip-path="url(#c{i})"><rect width="100" height="100" fill="{bg}"/><g transform="translate(50 58) scale(1.04) translate(-50 -56)">{b}</g></g></svg>')
    open(f'img/avatars/a{i + 1}.svg', 'w').write(svg)
print(len(A))
