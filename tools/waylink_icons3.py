# Waylink app icons, round 3: bold, flat ribbon marks. Run from the repo root.
import math
D = 'img/logos/'
def tile(bg, body, defs=''):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs>{defs}</defs>'
            f'<rect width="512" height="512" rx="114" fill="{bg}"/>{body}</svg>')
L = {}

# ROAD: a winding road narrowing to the horizon under a low sun.
def bez(p0, p1, p2, p3, t):
    u = 1 - t
    return tuple(u**3*a + 3*u*u*t*b + 3*u*t*t*c + t**3*d for a, b, c, d in zip(p0, p1, p2, p3))
P = [(236, 540), (330, 430), (226, 330), (276, 262)]
left, right = [], []
N = 48
for i in range(N + 1):
    t = i / N; x, y = bez(*P, t)
    x2, y2 = bez(*P, min(1, t + 0.01)); x1, y1 = bez(*P, max(0, t - 0.01))
    dx, dy = x2 - x1, y2 - y1; n = math.hypot(dx, dy); nx, ny = -dy / n, dx / n
    w = 250 * (1 - t) ** 1.5 + 6
    left.append((x + nx * w / 2, y + ny * w / 2)); right.append((x - nx * w / 2, y - ny * w / 2))
pts = left + right[::-1]
road = 'M' + 'L'.join(f'{x:.1f} {y:.1f}' for x, y in pts) + 'Z'
road = 'M118 512C190 420 236 340 250 262H262C290 340 330 420 394 512Z'
L['wl-road'] = ('Road', tile('#2A1E3B',
    '<clipPath id="r"><rect width="512" height="512" rx="114"/></clipPath>'
    '<g clip-path="url(#r)"><circle cx="256" cy="262" r="104" fill="#F6C177"/><path d="M0 262H512V512H0Z" fill="#3B2B52"/>'
    f'<path d="{road}" fill="#F4A261"/>'
    '<path d="M256 300V318M257 350V378M259 412V452" stroke="#3B2B52" stroke-width="10" stroke-linecap="round"/></g>'))

# CHAIN: a real roller chain, side on: outer and inner figure-eight plates, pin heads.
p, ang = 150, -30
pins = [(-2.5 + i) * p for i in range(6)]
def plate(x0, r, waist, fill):
    return (f'<path d="M{x0} {-r}Q{x0+p/2} {-waist} {x0+p} {-r}A{r} {r} 0 0 1 {x0+p} {r}Q{x0+p/2} {waist} {x0} {r}A{r} {r} 0 0 1 {x0} {-r}Z" fill="{fill}"/>')
g = ''
for i in range(5):
    if i % 2 == 1: g += plate(pins[i], 54, 36, '#9AA3AE')
for x in pins: g += f'<circle cx="{x}" cy="0" r="34" fill="#C8CFD7"/>'
for i in range(5):
    if i % 2 == 0: g += plate(pins[i], 62, 40, '#2B2F36')
for i in (0, 3): g += plate(pins[i] - (p if i == 3 else p), 46, 30, '#2B2F36') if False else ''
for x in pins: g += f'<circle cx="{x}" cy="0" r="17" fill="#E8ECF0"/><circle cx="{x}" cy="0" r="17" fill="none" stroke="#2B2F36" stroke-width="4"/>'
L['wl-chain'] = ('Chain', tile('#F1E2C8', f'<clipPath id="c"><rect width="512" height="512" rx="114"/></clipPath><g clip-path="url(#c)"><g transform="translate(256 256) rotate({ang})">{g}</g></g>'))

# ARROW: a two-tone navigation arrow, folded along its spine.
L['wl-nav'] = ('Arrow', tile('#F3E3C3',
    '<path d="M256 96L392 408L256 336Z" fill="#8E2F1C"/><path d="M256 96L120 408L256 336Z" fill="#C2462B"/>'))

# ROUTE: a thick road ribbon from a start ring to a pin.
L['wl-route'] = ('Route', tile('#E4EEE6',
    '<path d="M150 380C150 300 362 320 362 236C362 176 300 168 262 168" fill="none" stroke="#24715F" stroke-width="44"/>'
    '<circle cx="150" cy="392" r="42" fill="#24715F"/><circle cx="150" cy="392" r="16" fill="#E4EEE6"/>'
    '<path d="M300 92a58 58 0 0 1 58 58c0 46-58 98-58 98s-58-52-58-98a58 58 0 0 1 58-58z" fill="#123D33" transform="translate(-40 -10)"/>'
    '<circle cx="260" cy="140" r="20" fill="#E4EEE6"/>'))

# DUSK: a coral evening sky with the sun cut out, two cream roads crossing below.
sky, rd, bg = '#F28C6A', '#F4EBDD', '#1F2A44'
L['wl-dusk'] = ('Dusk', tile(bg,
    f'<path d="M126 100H386V228C318 228 252 262 126 262Z" fill="{sky}"/><circle cx="330" cy="160" r="28" fill="{bg}"/>'
    '<clipPath id="k"><rect x="126" y="200" width="260" height="230"/></clipPath>'
    f'<g clip-path="url(#k)" fill="none" stroke="{rd}" stroke-width="46">'
    '<path d="M100 314C220 314 292 390 412 390"/><path d="M100 390C220 390 292 314 412 314"/></g>'))

# RIBBON: a winding blue road between two yellow hairpins.
L['wl-ribbon'] = ('Ribbon', tile('#EAF0F7',
    '<path d="M100 250C206 214 306 306 412 262" fill="none" stroke="#1D56CF" stroke-width="58"/>'
    '<path d="M240 96A120 120 0 0 0 404 196" fill="none" stroke="#F2B33D" stroke-width="58"/>'
    '<path d="M272 416A120 120 0 0 0 108 316" fill="none" stroke="#F2B33D" stroke-width="58"/>'))

for k, (nm, svg) in L.items():
    open(D + k + '.svg', 'w').write(svg)
print([(k, v[0]) for k, v in L.items()])
