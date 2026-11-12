# One long landscape for the onboarding story: dawn → ride → speed → pit stop → night.
# 5 panels of 390 units. Colours come from CSS variables so it follows the theme and accent.
import math, random, json
random.seed(7)
W, H, PW = 1950, 400, 390
def ridge(y0, amp, step, jag, seed):
    r = random.Random(seed); pts = []; x = -40
    while x <= W + 40:
        y = y0 + math.sin(x / 210 + seed) * amp * .55 + math.sin(x / 77 + seed * 2) * amp * .3 + r.uniform(-jag, jag)
        pts.append((x, y)); x += step * r.uniform(.6, 1.4)
    d = 'M' + ' L'.join(f'{x:.0f} {y:.0f}' for x, y in pts) + f' L{W + 40} {H + 20} L-40 {H + 20}Z'
    return d
far = ridge(205, 60, 70, 18, 1)
mid = ridge(250, 46, 52, 22, 3)
# the road: a ribbon that winds along the bottom
def road_y(x): return 318 + math.sin(x / 260) * 16 + math.sin(x / 90) * 4
top = [(x, road_y(x) - 20) for x in range(-40, W + 41, 15)]
bot = [(x, road_y(x) + 26) for x in range(W + 40, -41, -15)]
road = 'M' + ' L'.join(f'{x} {y:.1f}' for x, y in top + bot) + 'Z'
road_edge = 'M' + ' L'.join(f'{x} {road_y(x) - 19:.1f}' for x in range(-40, W + 41, 15))
lane = 'M' + ' L'.join(f'{x} {road_y(x) + 3:.1f}' for x in range(-40, W + 41, 15))
near = ridge(372, 14, 40, 10, 5)
def P(i, x): return i * PW + x  # panel coordinates
parts = []
# 0 · dawn and a turn sign
parts.append(f'<circle class="sc-sun" cx="{P(0,268)}" cy="118" r="40"/>')
parts.append(f'<g class="sc-sign" filter="url(#sc-shadow)" transform="translate({P(0,112)} 214)"><rect x="-3" y="20" width="6" height="84" rx="2" class="sc-post"/><rect x="-28" y="-26" width="56" height="52" rx="14" class="sc-plate"/><path d="M-9 13V2Q-9 -7 0 -7H10M4 -13L10 -7L4 -1" class="sc-arrow"/></g>')
# 1 · rider with an incoming call
bx, by = P(1, 175), road_y(P(1, 175)) - 6
cxm = P(1, 195); parts.append(f'<image href="img/onb/rider-mono.webp" x="{cxm - 70}" y="{road_y(cxm) - 132}" width="140" height="132" preserveAspectRatio="xMidYMax meet"/>')
parts.append(f'<g class="sc-bike" style="display:none" transform="translate({bx} {by})"><circle cx="-27" cy="0" r="15" class="sc-tyre"/><circle cx="-27" cy="0" r="7" class="sc-rim"/><circle cx="29" cy="0" r="15" class="sc-tyre"/><circle cx="29" cy="0" r="7" class="sc-rim"/><path d="M-27 0L-6 -6L2 -6Z" class="sc-body2"/><path d="M-10 -8h18l4 -12h-26z" class="sc-body2"/><path d="M-2 -22C2 -32 14 -34 22 -28L20 -20H-2Z" class="sc-tank"/><path d="M-24 -22H-2V-18H-20Z" class="sc-seat"/><path d="M-24 -22L-38 -28L-34 -20Z" class="sc-body2"/><path d="M20 -20L29 0" class="sc-fork"/><path d="M18 -30L24 -38H31" class="sc-fork"/><path d="M24 -26a5 5 0 1 0 0.1 0" class="sc-lamp"/><path d="M-14 -22C-14 -34 -8 -42 -2 -42L8 -30L4 -22Z" class="sc-rider"/><circle cx="-4" cy="-50" r="8.5" class="sc-helmet"/><path d="M0 -36L16 -32" class="sc-arm"/></g>')
parts.append(f'<g class="sc-call" filter="url(#sc-shadow)" transform="translate({P(1,206)} 128)"><rect x="-82" y="-30" width="164" height="60" rx="18" class="sc-card"/><circle cx="-52" cy="0" r="17" class="sc-dot"/><path d="M-58 -6q0 13 12 13l3 -4 -5 -3 -3 2q-4 -2 -5 -6l2 -3 -3 -5z" class="sc-phone"/><rect x="-26" y="-12" width="78" height="9" rx="4.5" class="sc-line1"/><rect x="-26" y="5" width="50" height="8" rx="4" class="sc-line2"/><path d="M-10 30L0 42L10 30" class="sc-card"/></g>')
# 2 · speed: a dial in the sky, streaks on the road
cx, cy = P(2, 195), 196
ticks = ''.join(f'<path d="M{cx + 74*math.cos(a):.1f} {cy + 74*math.sin(a):.1f}L{cx + 92*math.cos(a):.1f} {cy + 92*math.sin(a):.1f}" class="sc-tick"/>' for a in [math.radians(150 + 20*k) for k in range(13)])
parts.append(f'<g class="sc-dial">{ticks}<path d="M{cx + 92*math.cos(math.radians(150)):.1f} {cy + 92*math.sin(math.radians(150)):.1f}A92 92 0 0 1 {cx + 92*math.cos(math.radians(290)):.1f} {cy + 92*math.sin(math.radians(290)):.1f}" class="sc-arc"/><path d="M{cx} {cy}L{cx + 70*math.cos(math.radians(290)):.1f} {cy + 70*math.sin(math.radians(290)):.1f}" class="sc-needle"/><circle cx="{cx}" cy="{cy}" r="7" class="sc-hub"/></g>')
parts.append(''.join(f'<path d="M{P(2, x)} {road_y(P(2,x)) - 34 + k*6:.0f}h{l}" class="sc-streak"/>' for k, (x, l) in enumerate([(40, 70), (120, 46), (210, 80), (300, 40)])))
# 3 · pit stop: fuel pump and a workshop with a wrench
px = P(3, 120)
parts.append(f'<g class="sc-pump" transform="translate({px} 222)"><rect x="-24" y="0" width="48" height="84" rx="14" class="sc-plate"/><rect x="-14" y="14" width="28" height="12" rx="4" fill="#fff" opacity=".9"/><path d="M24 26h8a5 5 0 0 1 5 5v34" fill="none" class="sc-hoseline"/><rect x="-30" y="84" width="60" height="6" rx="3" class="sc-baseline"/></g>')
gx = P(3, 268)
parts.append(f'<g class="sc-shop" transform="translate({gx} 200)"><path d="M-56 34L0 -6L56 34V96H-56Z" class="sc-house"/><rect x="-26" y="50" width="52" height="46" rx="4" class="sc-door"/><circle cx="0" cy="24" r="12" class="sc-dot"/><path d="M-4 28L4 20M2 16a4 4 0 1 0 4 4" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"/></g>')
# 4 · night: moon, stars, parked bike with a lock
parts.append(f'<circle cx="{P(4,286)}" cy="96" r="70" class="sc-moonglow" filter="url(#sc-soft)"/><circle class="sc-moon" cx="{P(4,286)}" cy="96" r="28"/><circle class="sc-moonc" cx="{P(4,298)}" cy="88" r="24"/>')
stars = ''.join(f'<circle cx="{P(4, random.randint(20, 370))}" cy="{random.randint(30, 190)}" r="{random.choice([1.2, 1.6, 2.2])}" class="sc-star"/>' for _ in range(22))
stars += ''.join(f'<circle cx="{P(3, random.randint(220, 390))}" cy="{random.randint(30, 140)}" r="1.2" class="sc-star dim"/>' for _ in range(6))
parts.append(stars)
lx, ly = P(4, 150), road_y(P(4, 150)) - 6
pass
pass
parts.append(f'<g class="sc-lock" filter="url(#sc-shadow)" transform="translate({P(4,195)} 186)">'
  '<ellipse cx="0" cy="96" rx="64" ry="9" fill="#000" opacity=".12"/>'
  '<path d="M-30 6V-18a30 30 0 0 1 60 0V6" fill="none" stroke="url(#m-shackle)" stroke-width="13" stroke-linecap="round"/>'
  '<rect x="-48" y="0" width="96" height="86" rx="22" fill="url(#m-lock)"/>'
  '<rect x="-48" y="0" width="96" height="30" rx="22" fill="#fff" opacity=".14"/>'
  '<circle cx="0" cy="38" r="10" fill="#0E1420" opacity=".85"/><path d="M-4 42h8l3 22h-14z" fill="#0E1420" opacity=".85"/></g>')

# --- background life: clouds, a far town, pine trees, poles with wires, birds ---
R = random.Random(11)
bg = []
for k in range(9):
    x = 80 + k * 215 + R.randint(-40, 40); y = R.randint(50, 120); w = R.randint(70, 120)
    bg.append(f'<g class="sc-cloud"><ellipse cx="{x}" cy="{y}" rx="{w/2:.0f}" ry="{w/7:.0f}"/><ellipse cx="{x - w*.18:.0f}" cy="{y - w*.08:.0f}" rx="{w/4:.0f}" ry="{w/6:.0f}"/><ellipse cx="{x + w*.15:.0f}" cy="{y - w*.1:.0f}" rx="{w/5:.0f}" ry="{w/7:.0f}"/></g>')
town = ''
x = P(1, 230)
for k in range(12):
    w = R.randint(12, 26); h = R.randint(22, 64)
    town += f'<rect x="{x}" y="{238 - h}" width="{w}" height="{h + 30}"/>'
    if R.random() < .4: town += f'<rect x="{x + w/2 - 1:.0f}" y="{238 - h - 12}" width="2" height="12"/>'
    x += w + R.randint(1, 4)
bg.append(f'<g class="sc-town">{town}</g>')
trees = ''
for k in range(46):
    tx = R.randint(0, W); base = 268 + math.sin(tx / 77 + 6) * 8 + R.uniform(-4, 4); h = R.randint(18, 34)
    if P(2, 0) < tx < P(4, 0): continue
    trees += f'<path d="M{tx} {base - h:.0f}L{tx + h*.32:.0f} {base:.0f}H{tx - h*.32:.0f}Z"/>'
bg.append(f'<g class="sc-trees">{trees}</g>')
poles = ''; wire = []
for k in range(0, W, 130):
    y0 = road_y(k) - 22
    poles += f'<rect x="{k - 1.5}" y="{y0 - 74:.0f}" width="3" height="74"/><rect x="{k - 9}" y="{y0 - 70:.0f}" width="18" height="2.5"/>'
    wire.append((k, y0 - 69))
wd = 'M' + ' '.join(f'{x} {y:.0f}' if i == 0 else f'Q{(wire[i-1][0] + x) / 2:.0f} {max(wire[i-1][1], y) + 12:.0f} {x} {y:.0f}' for i, (x, y) in enumerate(wire))
bg.append('<g class="sc-poles"></g>')
birds = ''.join(f'<path d="M{bx} {by}q5 -5 10 0q5 -5 10 0" class="sc-bird"/>' for bx, by in [(P(0, 150), 70), (P(0, 175), 84), (P(1, 60), 60), (P(2, 320), 74), (P(3, 40), 66), (P(3, 64), 76)])
bg.append(birds)
BG = ''.join(bg)
svg = f'''<svg class="onb-scene" viewBox="0 0 {W} {H}" preserveAspectRatio="xMinYMid slice" aria-hidden="true"><defs><linearGradient id="sc-sky" x1="0" x2="1" y1="0" y2="0"><stop offset="0" class="s0"/><stop offset=".55" class="s1"/><stop offset=".78" class="s2"/><stop offset="1" class="s3"/></linearGradient>
<linearGradient id="sc-v" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".0"/><stop offset=".6" stop-color="#fff" stop-opacity=".0"/><stop offset="1" stop-color="#fff" stop-opacity=".22"/></linearGradient>
<linearGradient id="g-far" x1="0" x2="0" y1="0" y2="1"><stop offset="0" class="gf0"/><stop offset="1" class="gf1"/></linearGradient>
<linearGradient id="g-mid" x1="0" x2="0" y1="0" y2="1"><stop offset="0" class="gm0"/><stop offset="1" class="gm1"/></linearGradient>
<linearGradient id="g-near" x1="0" x2="0" y1="0" y2="1"><stop offset="0" class="gn0"/><stop offset="1" class="gn1"/></linearGradient>
<linearGradient id="g-road" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#2A303C"/><stop offset=".45" stop-color="#171B24"/><stop offset="1" stop-color="#0C0F15"/></linearGradient>
<radialGradient id="g-sun" cx="50%" cy="50%" r="50%"><stop offset="0" class="sun0"/><stop offset=".35" class="sun1"/><stop offset="1" class="sun2"/></radialGradient>
<linearGradient id="g-haze" x1="0" x2="0" y1="0" y2="1"><stop offset="0" class="hz" stop-opacity="0"/><stop offset="1" class="hz" stop-opacity=".55"/></linearGradient>
<filter id="sc-grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" seed="5" stitchTiles="stitch"/><feColorMatrix values="0 0 0 0 .5  0 0 0 0 .5  0 0 0 0 .5  0 0 0 .22 0"/></filter>
<linearGradient id="m-pump" x1="0" x2="1"><stop offset="0" class="hl-d"/><stop offset=".45" class="hl"/><stop offset="1" class="hl-d"/></linearGradient>
<linearGradient id="m-cap" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".35"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<linearGradient id="m-panel" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#EEF1F5"/><stop offset="1" stop-color="#C7CDD6"/></linearGradient>
<linearGradient id="m-base" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#5A616E"/><stop offset="1" stop-color="#2B313C"/></linearGradient>
<linearGradient id="m-roof" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#8C939E"/><stop offset="1" stop-color="#5B626D"/></linearGradient>
<linearGradient id="m-wall" x1="0" x2="1"><stop offset="0" stop-color="#D9DCE1"/><stop offset=".6" stop-color="#EEF0F3"/><stop offset="1" stop-color="#C9CDD4"/></linearGradient>
<linearGradient id="m-shutter" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#9AA1AC"/><stop offset="1" stop-color="#6B727D"/></linearGradient>
<linearGradient id="m-lock" x1="0" x2="1" y1="0" y2="1"><stop offset="0" class="hl-l"/><stop offset=".5" class="hl"/><stop offset="1" class="hl-d"/></linearGradient>
<linearGradient id="m-shackle" x1="0" x2="1"><stop offset="0" stop-color="#8E959F"/><stop offset=".5" stop-color="#F2F4F7"/><stop offset="1" stop-color="#8E959F"/></linearGradient>
<filter id="sc-wc" x="-5%" y="-5%" width="110%" height="110%" color-interpolation-filters="sRGB"><feTurbulence type="fractalNoise" baseFrequency=".02" numOctaves="2" seed="4" result="n"/><feDisplacementMap in="SourceGraphic" in2="n" scale="10" result="d"/><feGaussianBlur in="d" stdDeviation=".8" result="b"/><feTurbulence type="fractalNoise" baseFrequency=".6" numOctaves="2" seed="2" result="g"/><feColorMatrix in="g" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1.4 1.2" result="gm"/><feComposite in="b" in2="gm" operator="in" result="t"/><feMorphology in="d" operator="erode" radius="1.5" result="e"/><feComposite in="d" in2="e" operator="out" result="edge"/><feColorMatrix in="edge" type="matrix" values=".6 0 0 0 0  0 .6 0 0 0  0 0 .6 0 0  0 0 0 .5 0" result="ed"/><feMerge><feMergeNode in="t"/><feMergeNode in="ed"/></feMerge></filter><filter id="sc-wc-d" x="-5%" y="-5%" width="110%" height="110%" color-interpolation-filters="sRGB"><feTurbulence type="fractalNoise" baseFrequency=".02" numOctaves="2" seed="4" result="n"/><feDisplacementMap in="SourceGraphic" in2="n" scale="10" result="d"/><feGaussianBlur in="d" stdDeviation=".8" result="b"/><feTurbulence type="fractalNoise" baseFrequency=".6" numOctaves="2" seed="2" result="g"/><feColorMatrix in="g" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -1.2 1.25" result="gm"/><feComposite in="b" in2="gm" operator="in" result="t"/><feMorphology in="d" operator="erode" radius="1.5" result="e"/><feComposite in="d" in2="e" operator="out" result="edge"/><feColorMatrix in="edge" type="matrix" values=".6 0 0 0 0  0 .6 0 0 0  0 0 .6 0 0  0 0 0 .5 0" result="ed"/><feMerge><feMergeNode in="t"/><feMergeNode in="ed"/></feMerge></filter><filter id="sc-mono" color-interpolation-filters="sRGB"><feColorMatrix type="saturate" values="0"/><feComponentTransfer><feFuncR type="linear" slope=".9" intercept=".06"/><feFuncG type="linear" slope=".9" intercept=".06"/><feFuncB type="linear" slope=".9" intercept=".06"/></feComponentTransfer><feDropShadow dx="0" dy="4" stdDeviation="3" flood-opacity=".25"/></filter><filter id="sc-soft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="6"/></filter>
<filter id="sc-shadow" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="6" stdDeviation="6" flood-color="#000" flood-opacity=".35"/></filter></defs>
<g class="sc-wcg" filter="url(#sc-wc)"><rect width="{W}" height="{H}" fill="url(#sc-sky)"/><circle cx="{P(0,268)}" cy="118" r="150" fill="url(#g-sun)"/>{BG.split('<g class="sc-town">')[0]}<g class="sc-l far"><path d="{far}" fill="url(#g-far)"/><rect y="200" width="{W}" height="90" fill="url(#g-haze)"/></g><g class="sc-town">{BG.split('<g class="sc-town">')[1].split('<g class="sc-trees">')[0]}<g class="sc-l mid"><path d="{mid}" fill="url(#g-mid)"/><g class="sc-trees">{BG.split('<g class="sc-trees">')[1].split('<g class="sc-poles">')[0]}<path d="{mid}" fill="url(#sc-v)" opacity=".5"/><rect y="250" width="{W}" height="70" fill="url(#g-haze)" opacity=".7"/></g>
<g class="sc-l base"><g class="sc-poles">{BG.split('<g class="sc-poles">')[1]}<path d="{road}" fill="url(#g-road)"/><path d="{road_edge}" class="sc-edge"/><path d="{lane}" class="sc-lane"/>{''.join(parts)}</g><g class="sc-l near"><path d="{near}" fill="url(#g-near)"/></g></g></svg>'''
import re as _re
_imgs = _re.findall(r'<image href="img/onb/rider-mono[^>]*/>', svg)
for _m in _imgs: svg = svg.replace(_m, '')
svg = svg.replace('</svg>', ''.join(_imgs) + '</svg>')
open('js/onb-scene.js', 'w').write('// Generated by tools/onb_scene.py\nexport const SCENE = ' + json.dumps(svg) + ';\n')
print(len(svg))
