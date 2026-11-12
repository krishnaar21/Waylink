# Pixel avatars for riders: helmets, goggles, mechanic caps, travel hats. 16x16 grid on a round tile.
SK = {'a': '#F1C7A3', 'b': '#D9A07A', 'c': '#A86B4A', 'd': '#6E4630'}
def face(g, skin):
    for y in range(5, 14):
        for x in range(4, 12): g[y][x] = skin
    for y in range(8, 11): g[y][3] = g[y][12] = skin  # ears
    g[8][6] = g[8][9] = '#1B1D22'          # eyes
    g[11][7] = g[11][8] = '#7A3B2E'        # mouth
    for x in range(5, 11): g[14][x] = skin  # neck
def body(g, c):
    for y in range(15, 16):
        for x in range(2, 14): g[y][x] = c
def hair(g, c, rows=(3, 4)):
    for y in rows:
        for x in range(4, 12): g[y][x] = c
    g[5][4] = g[5][11] = c
def helmet_full(g, c, visor):
    for y in range(2, 14):
        for x in range(3, 13): g[y][x] = c
    for y in range(7, 10):
        for x in range(4, 12): g[y][x] = visor
    g[7][5] = '#FFFFFF'; g[1][6] = g[1][7] = g[1][8] = g[1][9] = c
def helmet_open(g, c):
    for y in range(2, 6):
        for x in range(3, 13): g[y][x] = c
    for y in range(6, 12): g[y][3] = g[y][12] = c
    g[1][6] = g[1][7] = g[1][8] = g[1][9] = c
def goggles(g, frame, lens):
    for x in range(4, 12): g[7][x] = frame
    for x in (5, 6, 9, 10): g[8][x] = lens
    g[8][7] = g[8][8] = frame
def shades(g):
    for x in (5, 6, 9, 10): g[8][x] = '#16181C'
    for x in range(5, 11): g[7][x] = '#16181C'
def cap(g, c, brim):
    for y in range(2, 5):
        for x in range(4, 12): g[y][x] = c
    for x in range(8, 14): g[5][x] = brim
def hat(g, c):
    for y in range(1, 4):
        for x in range(5, 11): g[y][x] = c
    for x in range(2, 14): g[4][x] = c
def beard(g, c):
    for y in range(10, 14):
        for x in range(4, 12): g[y][x] = c
    g[11][7] = g[11][8] = '#7A3B2E'
def bandana(g, c):
    for y in range(10, 14):
        for x in range(4, 12): g[y][x] = c
    for x in range(5, 11, 2): g[11][x] = '#F3E9D2'
def grease(g):
    g[10][5] = g[10][6] = '#3A3530'
def wrench_tag(g):
    for (y, x) in [(15, 11), (14, 12), (13, 13), (13, 12), (12, 13)]: g[y][x] = '#C9CED6'
A = [
  ('#2F3A56', lambda g: (face(g, SK['b']), body(g, '#1B2340'), helmet_full(g, '#E5484D', '#1B1F2A'))),
  ('#E9D9B6', lambda g: (face(g, SK['a']), body(g, '#5A3A22'), helmet_open(g, '#3A3F4A'), goggles(g, '#8A5A2B', '#7FD3FF'))),
  ('#3F6E5A', lambda g: (face(g, SK['c']), body(g, '#2E5BD6'), cap(g, '#2E5BD6', '#1E3E99'), grease(g), wrench_tag(g))),
  ('#5B4B8A', lambda g: (face(g, SK['b']), body(g, '#222'), hair(g, '#1B1D22'), shades(g), beard(g, '#2A1D16'))),
  ('#E8A13A', lambda g: (face(g, SK['a']), body(g, '#B8336A'), hat(g, '#6B4A2A'))),
  ('#1F2B3A', lambda g: (face(g, SK['d']), body(g, '#3A3F4A'), helmet_open(g, '#F2C230'), goggles(g, '#222', '#FF8A3D'))),
  ('#C9D6E3', lambda g: (face(g, SK['b']), body(g, '#C2452B'), hair(g, '#6B3A1E'), bandana(g, '#C2452B'))),
  ('#2B2D33', lambda g: (face(g, SK['c']), body(g, '#F27D26'), cap(g, '#F27D26', '#B85514'), grease(g))),
  ('#7FB7A8', lambda g: (face(g, SK['a']), body(g, '#1E1F24'), helmet_full(g, '#F3F3F3', '#2B6FD6'))),
  ('#A33A2E', lambda g: (face(g, SK['b']), body(g, '#2A2A2A'), hair(g, '#C9A27A'), beard(g, '#C9A27A'), goggles(g, '#4A4A4A', '#B7E3FF'))),
  ('#3B4FA8', lambda g: (face(g, SK['d']), body(g, '#D9D2C0'), hat(g, '#2A2A2A'), shades(g))),
  ('#D86C4E', lambda g: (face(g, SK['a']), body(g, '#1F3A5F'), helmet_open(g, '#1F3A5F'), bandana(g, '#1F3A5F'))),
]
for i, (bgc, draw) in enumerate(A):
    g = [[None] * 16 for _ in range(16)]; draw(g)
    px = ''.join(f'<rect x="{x}" y="{y}" width="1.02" height="1.02" fill="{c}"/>' for y in range(16) for x in range(16) if (c := g[y][x]))
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" shape-rendering="crispEdges"><circle cx="10" cy="10" r="10" fill="{bgc}" shape-rendering="geometricPrecision"/><defs><clipPath id="c"><circle cx="10" cy="10" r="10"/></clipPath></defs><g clip-path="url(#c)" transform="translate(2 3.2)">{px}</g></svg>'
    open(f'img/avatars/a{i + 1}.svg', 'w').write(svg)
print(len(A))
