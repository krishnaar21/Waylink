"""Onboarding art as crisp vectors: the road story (3 themes x 5 slides), the rider, the Bluetooth disc and the avatars.

Run from the repo root (Python 3 only, no packages):   python3 android/tools/onb_art.py

Reads
  web-v0/tools/onb-scene.js        the v0 road scene geometry (frozen reference, never edited)
  android/art/onb/rider_trace.svg  the rider, traced once from the old 1050 px picture (see trace_rider.py)
  web-v0/img/avatars/a*.svg        the avatar line drawings
Writes
  android/art/onb/*.svg                         flat source SVGs (open them in any editor)
  android/app/src/main/res/drawable/*.xml       Android vector drawables

Why vectors, and why these choices:
  - The old pictures were watercolour-filtered PNG renders upscaled by a model, so they looked soft and grainy.
  - Flat fills only. Filters, blurs, grain and drop shadows are dropped; they are what made the art blurry and
    they cost the most to draw. One gradient is kept (the padlock), plus one soft shadow under the rider.
  - The scene is cut into 5 slide-sized pieces, so the phone only draws and caches the two pieces on screen
    instead of one bitmap five screens wide.
  - Path data is written with 1 decimal and explicit separators, so every Android path parser reads it.
"""
import json, math, os, re
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WEB = os.path.join(ROOT, 'web-v0')
ART = os.path.join(ROOT, 'android', 'art', 'onb')
RES = os.path.join(ROOT, 'android', 'app', 'src', 'main', 'res', 'drawable')
PW, H = 390, 400          # one slide of the scene, in scene units
OVERLAP = 2               # each slide piece reaches 2 units into the next so the seam never shows

# ---------- colours, picked from the old pictures (median of each part, nudged where the grain washed them out) ----------
PALETTE = {
    'light': dict(sky='#EBE4D6', sun='#E2DBCD', cloud='#EFEADF', far='#E2DBCD', town='#DFD8CA', mid='#D8D1C4', tree='#CCC5B8',
                  road='#C3BCB1', edge='#D9D2C6', lane='#E9E3D8', near='#CDC5BA', bird='#BFB8AB', plate='#A8717F', arrow='#FFFFFF',
                  post='#C6BFB4', card='#FBF8F2', calldot='#5FAE80', phone='#FFFFFF', line1='#8C877B', line2='#DCD6CB', tick='#ACA599',
                  arc='#A8717F', needle='#8E887B', hub='#8C8578', streak='#E9E3D8', screen='#F2F1EE', hose='#C2BCAF', base='#C2BCB2',
                  house='#D3CDBF', door='#C3BCB1', shopdot='#A8717F', wrench='#FFFFFF', moon='#DDD6C8', moonc='#E4DED1', star='#D8D1C4',
                  lock=('#C9ADB4', '#B48D98', '#9E7581'), lockhi='#FFFFFF', shackle='#C4C5BD', keyhole='#6E6A66'),
    'dark': dict(sky='#201013', sun='#2A191C', cloud='#241417', far='#2A1A1D', town='#2C1C1F', mid='#34262A', tree='#3D333B',
                 road='#4C4D56', edge='#5E5F68', lane='#2C2B34', near='#3C3B44', bird='#4C3C40', plate='#7A2E40', arrow='#F2E8EA',
                 post='#3F484D', card='#231316', calldot='#2F7A52', phone='#FFFFFF', line1='#9AA1AA', line2='#3B2B31', tick='#8A8590',
                 arc='#7A2A40', needle='#958E99', hub='#949BA0', streak='#5C5862', screen='#9AA1A8', hose='#4A494F', base='#4B3D4A',
                 house='#383037', door='#555462', shopdot='#7A2E45', wrench='#F2E8EA', moon='#3A292C', moonc='#2C1B1F', star='#6A5A5E',
                 lock=('#A8404F', '#8E2E40', '#6E2232'), lockhi='#FFFFFF', shackle='#6A7073', keyhole='#1E0D11'),
    'night': dict(sky='#2E2B25', sun='#37342E', cloud='#33302A', far='#393530', town='#3B3832', mid='#45413B', tree='#524F47',
                  road='#65625A', edge='#77746C', lane='#3C3933', near='#57534B', bird='#5C584F', plate='#74303F', arrow='#F3EFE6',
                  post='#5F5C56', card='#34302A', calldot='#3F7A54', phone='#FFFFFF', line1='#A6A497', line2='#4A473F', tick='#8E8B82',
                  arc='#702E40', needle='#9D9A90', hub='#A9A69B', streak='#6E6A62', screen='#ACA49C', hose='#5F5C56', base='#5B534D',
                  house='#4E4A40', door='#6C6961', shopdot='#763446', wrench='#F3EFE6', moon='#47443D', moonc='#3D3A34', star='#7A766C',
                  lock=('#7E8286', '#7A3242', '#58212D'), lockhi='#FFFFFF', shackle='#8A887B', keyhole='#2F2E29'),
}

# ---------- small geometry helpers ----------
NUM = re.compile(r'[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?|[MmLlHhVvCcSsQqTtAaZz]')

def fmt(v, nd=1):
    s = f'{round(v, nd):.{nd}f}'.rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

def clean_path(d, nd=1, scale=1.0, dx=0.0, dy=0.0):
    """Re-emit SVG path data with rounded numbers and explicit separators (safe for every Android parser).
    Optional uniform scale + offset (applied to absolute commands; relative ones only scale)."""
    out, cmd, args, first = [], None, [], True
    toks = NUM.findall(d)
    def flush():
        if cmd is None: return
        vals = []
        for i, a in enumerate(args):
            if cmd in 'Aa' and i % 7 in (3, 4):          # arc flags stay 0/1
                vals.append(str(int(float(a)))); continue
            v = float(a)
            if cmd in 'Aa' and i % 7 == 2: vals.append(fmt(v, nd)); continue
            v *= scale
            if cmd.isupper():
                if cmd == 'H': v += dx
                elif cmd == 'V': v += dy
                elif cmd == 'A': v += (dx if i % 7 == 5 else dy if i % 7 == 6 else 0) if i % 7 >= 5 else 0
                else: v += dx if i % 2 == 0 else dy
            vals.append(fmt(v, nd))
        s = cmd
        for i, v in enumerate(vals):
            s += ('' if i == 0 or v.startswith('-') else ' ') + v
        out.append(s)
    for t in toks:
        if t.isalpha():
            flush(); cmd, args = t, []
        else:
            args.append(t)
    flush()
    return ''.join(out)

def circle_d(cx, cy, r):
    return f'M{fmt(cx - r)} {fmt(cy)}A{fmt(r)} {fmt(r)} 0 1 0 {fmt(cx + r)} {fmt(cy)}A{fmt(r)} {fmt(r)} 0 1 0 {fmt(cx - r)} {fmt(cy)}Z'

def ellipse_d(cx, cy, rx, ry):
    return f'M{fmt(cx - rx)} {fmt(cy)}A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx + rx)} {fmt(cy)}A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx - rx)} {fmt(cy)}Z'

def rect_d(x, y, w, h, rx=0):
    if not rx:
        return f'M{fmt(x)} {fmt(y)}H{fmt(x + w)}V{fmt(y + h)}H{fmt(x)}Z'
    rx = min(rx, w / 2, h / 2)
    return (f'M{fmt(x + rx)} {fmt(y)}H{fmt(x + w - rx)}A{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(x + w)} {fmt(y + rx)}V{fmt(y + h - rx)}'
            f'A{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(x + w - rx)} {fmt(y + h)}H{fmt(x + rx)}A{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(x)} {fmt(y + h - rx)}'
            f'V{fmt(y + rx)}A{fmt(rx)} {fmt(rx)} 0 0 1 {fmt(x + rx)} {fmt(y)}Z')

def points(d):
    """Absolute M/L points of a polyline path (the road edge and lane are polylines)."""
    return [(float(a), float(b)) for a, b in re.findall(r'[ML]\s*(-?[\d.]+)[ ,]+(-?[\d.]+)', d)]

def dashes(pts, on, off):
    """Cut a polyline into dash segments (Android vectors have no dash style)."""
    out, pos, period, cur = [], 0.0, on + off, []
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        seg = math.hypot(x1 - x0, y1 - y0); t = 0.0
        while t < seg:
            ph = pos % period; inside = ph < on
            step = min(seg - t, (on - ph) if inside else (period - ph))
            if inside:
                a = (x0 + (x1 - x0) * t / seg, y0 + (y1 - y0) * t / seg)
                b = (x0 + (x1 - x0) * (t + step) / seg, y0 + (y1 - y0) * (t + step) / seg)
                if not cur: cur = [a]
                cur.append(b)
            elif cur:
                out.append(cur); cur = []
            t += step; pos += step
    if cur: out.append(cur)
    return ''.join('M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in c) for c in out if len(c) > 1)

# ---------- a tiny drawing model: shapes inside optional groups, written out as SVG and as Android XML ----------
class Shape:
    def __init__(self, d, fill=None, stroke=None, sw=0, alpha=1.0, cap=None, join=None, grad=None, x0=0, x1=0):
        self.d, self.fill, self.stroke, self.sw, self.alpha, self.cap, self.join, self.grad = d, fill, stroke, sw, alpha, cap, join, grad
        self.x0, self.x1 = x0, x1        # horizontal extent in scene units, used to cut the scene into slides

class Group:
    def __init__(self, items, tx=0, ty=0, sx=1, sy=1, px=0, py=0, x0=0, x1=0):
        self.items, self.tx, self.ty, self.sx, self.sy, self.px, self.py, self.x0, self.x1 = items, tx, ty, sx, sy, px, py, x0, x1

def ext(d):
    xs = [p[0] for p in points(d)]
    return (min(xs), max(xs)) if xs else (-1e9, 1e9)

def svg_out(items, vb, extra_defs=''):
    defs, body, gid = [], [], [0]
    def emit(it):
        if isinstance(it, Group):
            t = []
            if it.tx or it.ty: t.append(f'translate({fmt(it.tx, 3)} {fmt(it.ty, 3)})')
            if it.sx != 1 or it.sy != 1:
                t.append(f'translate({fmt(it.px, 3)} {fmt(it.py, 3)}) scale({fmt(it.sx, 4)} {fmt(it.sy, 4)}) translate({fmt(-it.px, 3)} {fmt(-it.py, 3)})')
            body.append(f'<g transform="{" ".join(t)}">' if t else '<g>')
            for c in it.items: emit(c)
            body.append('</g>'); return
        a = [f'd="{it.d}"']
        if it.grad:
            gid[0] += 1; g = it.grad; n = f'g{gid[0]}'
            stops = ''.join(f'<stop offset="{o}" stop-color="{c[:7]}"' + (f' stop-opacity="{int(c[7:9], 16) / 255:.2f}"' if len(c) > 7 else '') + '/>' for o, c in g['stops'])
            if g['type'] == 'linear':
                defs.append(f'<linearGradient id="{n}" gradientUnits="userSpaceOnUse" x1="{g["x1"]}" y1="{g["y1"]}" x2="{g["x2"]}" y2="{g["y2"]}">{stops}</linearGradient>')
            else:
                defs.append(f'<radialGradient id="{n}" gradientUnits="userSpaceOnUse" cx="{g["cx"]}" cy="{g["cy"]}" r="{g["r"]}">{stops}</radialGradient>')
            a.append(f'fill="url(#{n})"')
        else:
            a.append(f'fill="{it.fill or "none"}"')
        if it.stroke:
            a += [f'stroke="{it.stroke}"', f'stroke-width="{fmt(it.sw, 2)}"']
            if it.cap: a.append(f'stroke-linecap="{it.cap}"')
            if it.join: a.append(f'stroke-linejoin="{it.join}"')
        if it.alpha != 1: a.append(f'opacity="{fmt(it.alpha, 2)}"')
        body.append(f'<path {" ".join(a)}/>')
    for it in items: emit(it)
    d = f'<defs>{"".join(defs)}{extra_defs}</defs>' if defs or extra_defs else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">{d}{"".join(body)}</svg>\n'

def vd_out(items, vw, vh, w_dp, h_dp, clip=None):
    need_aapt = [False]
    lines = []
    def emit(it, ind):
        p = ' ' * ind
        if isinstance(it, Group):
            attrs = []
            if it.tx: attrs.append(f'android:translateX="{fmt(it.tx, 3)}"')
            if it.ty: attrs.append(f'android:translateY="{fmt(it.ty, 3)}"')
            if it.sx != 1: attrs.append(f'android:scaleX="{fmt(it.sx, 4)}"')
            if it.sy != 1: attrs.append(f'android:scaleY="{fmt(it.sy, 4)}"')
            if it.px: attrs.append(f'android:pivotX="{fmt(it.px, 3)}"')
            if it.py: attrs.append(f'android:pivotY="{fmt(it.py, 3)}"')
            lines.append(f'{p}<group {" ".join(attrs)}>' if attrs else f'{p}<group>')
            for c in it.items: emit(c, ind + 2)
            lines.append(f'{p}</group>'); return
        a = [f'android:pathData="{it.d}"']
        if it.fill and not it.grad: a.append(f'android:fillColor="{it.fill}"')
        if it.stroke:
            a += [f'android:strokeColor="{it.stroke}"', f'android:strokeWidth="{fmt(it.sw, 2)}"']
            if it.cap: a.append(f'android:strokeLineCap="{it.cap}"')
            if it.join: a.append(f'android:strokeLineJoin="{it.join}"')
        if it.alpha != 1:
            if it.fill or it.grad: a.append(f'android:fillAlpha="{fmt(it.alpha, 2)}"')
            if it.stroke: a.append(f'android:strokeAlpha="{fmt(it.alpha, 2)}"')
        if not it.grad:
            lines.append(f'{p}<path {" ".join(a)}/>'); return
        need_aapt[0] = True; g = it.grad
        lines.append(f'{p}<path {" ".join(a)}>')
        lines.append(f'{p}  <aapt:attr name="android:fillColor">')
        if g['type'] == 'linear':
            lines.append(f'{p}    <gradient android:type="linear" android:startX="{g["x1"]}" android:startY="{g["y1"]}" android:endX="{g["x2"]}" android:endY="{g["y2"]}">')
        else:
            lines.append(f'{p}    <gradient android:type="radial" android:centerX="{g["cx"]}" android:centerY="{g["cy"]}" android:gradientRadius="{g["r"]}">')
        for o, c in g['stops']:
            col = c if len(c) == 7 else '#' + c[7:9] + c[1:7]      # #RRGGBBAA -> #AARRGGBB
            lines.append(f'{p}      <item android:offset="{o}" android:color="{col}"/>')
        lines.append(f'{p}    </gradient>')
        lines.append(f'{p}  </aapt:attr>')
        lines.append(f'{p}</path>')
    body_ind = 4 if clip else 2
    for it in items: emit(it, body_ind)
    head = ['<?xml version="1.0" encoding="utf-8"?>',
            '<!-- Generated by android/tools/onb_art.py; edit that, not this. -->',
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"' + (' xmlns:aapt="http://schemas.android.com/aapt"' if need_aapt[0] else ''),
            f'    android:width="{w_dp}dp" android:height="{h_dp}dp" android:viewportWidth="{fmt(vw, 2)}" android:viewportHeight="{fmt(vh, 2)}">']
    if clip:
        head.append('  <group>'); head.append(f'    <clip-path android:pathData="{clip}"/>')
        lines.append('  </group>')
    return '\n'.join(head + lines + ['</vector>', ''])

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: f.write(text)

# ---------- the rider ----------
RIDER_VB = (1050, 990)
def rider_items():
    root = ET.parse(os.path.join(ART, 'rider_trace.svg')).getroot()
    items = []
    # the soft ground shadow the old picture had: one ellipse, a radial fade squashed flat
    cx, cy, rx, ry = 526, 958, 512, 30
    items.append(Group([Shape(circle_d(cx, cy, rx), grad=dict(type='radial', cx=cx, cy=cy, r=rx,
                                                               stops=[(0, '#000000D0'), (0.8, '#000000B0'), (1, '#00000000')]))],
                       sy=ry / rx, px=cx, py=cy))
    for el in root.iter():
        if el.tag.endswith('path'):
            items.append(Shape(clean_path(el.get('d')), fill=el.get('fill')))
    return items

# ---------- the road scene ----------
def scene_items(theme):
    c = PALETTE[theme]
    src = open(os.path.join(WEB, 'tools', 'onb-scene.js')).read()
    svg = json.loads(src[src.index('"'):src.rindex('"') + 1])
    root = ET.fromstring(svg)
    out = []

    def style(cls, el, ctx):
        """Map a v0 class (or gradient fill) to a flat style. None = leave it out."""
        f = el.get('fill', '')
        if 'url(#sc-sky)' in f: return dict(fill=c['sky'])
        if 'url(#g-far)' in f: return dict(fill=c['far'])
        if 'url(#g-mid)' in f: return dict(fill=c['mid'])
        if 'url(#g-near)' in f: return dict(fill=c['near'])
        if 'url(#g-road)' in f: return dict(fill=c['road'])
        if 'url(#m-lock)' in f: return dict(lock=True)
        if any(k in f for k in ('url(#g-sun)', 'url(#g-haze)', 'url(#sc-v)')): return None
        k = cls or ctx
        if k in ('sc-bike', 'sc-moonglow', 'sc-poles'): return None
        S = dict(fill=None)
        simple = {'sc-cloud': 'cloud', 'sc-town': 'town', 'sc-trees': 'tree', 'sc-sun': 'sun', 'sc-post': 'post', 'sc-plate': 'plate',
                  'sc-card': 'card', 'sc-phone': 'phone', 'sc-line1': 'line1', 'sc-line2': 'line2', 'sc-hub': 'hub', 'sc-baseline': 'base',
                  'sc-house': 'house', 'sc-door': 'door', 'sc-moon': 'moon', 'sc-moonc': 'moonc', 'sc-star': 'star'}
        if k in simple: S['fill'] = c[simple[k]]
        elif k == 'sc-dot': S['fill'] = c['calldot'] if 'sc-call' in ctx_chain else c['shopdot']
        elif k == 'sc-bird': S.update(stroke=c['bird'], sw=1.6, cap='round')
        elif k == 'sc-edge': S.update(stroke=c['edge'], sw=1.5)
        elif k == 'sc-lane': S.update(stroke=c['lane'], sw=2.4, dash=(16, 14))
        elif k == 'sc-arrow': S.update(stroke=c['arrow'], sw=4, cap='round', join='round')
        elif k == 'sc-tick': S.update(stroke=c['tick'], sw=3, cap='round')
        elif k == 'sc-arc': S.update(stroke=c['arc'], sw=7, cap='round')
        elif k == 'sc-needle': S.update(stroke=c['needle'], sw=4, cap='round')
        elif k == 'sc-streak': S.update(stroke=c['streak'], sw=3, cap='round', alpha=.35)
        elif k == 'sc-hoseline': S.update(stroke=c['hose'], sw=5, cap='round')
        elif el.get('stroke') == 'url(#m-shackle)': S.update(stroke=c['shackle'], sw=13, cap='round')
        elif el.get('stroke') == '#fff': S.update(stroke=c['wrench'], sw=float(el.get('stroke-width', 2)), cap='round')
        elif f == '#fff': S['fill'] = c['screen'] if el.get('opacity') == '.9' else c['lockhi']; S['alpha'] = float(el.get('opacity', 1))
        elif f == '#000': S.update(fill='#000000', alpha=float(el.get('opacity', 1)))
        elif f == '#0E1420': S.update(fill=c['keyhole'])
        else: return None
        if el.get('fill') == 'none': S['fill'] = None
        if 'dim' in (el.get('class') or ''): S['alpha'] = .45
        return S

    ctx_chain = []
    def walk(el, ctx, tx, ty, sink):
        nonlocal ctx_chain
        cls = (el.get('class') or '').split(' ')[0] if el.get('class') else ''
        tag = el.tag
        if tag in ('defs', 'image') or el.get('style') == 'display:none': return
        if tag in ('g', 'svg'):
            m = re.match(r'translate\(([-\d.]+)[ ,]+([-\d.]+)\)', el.get('transform') or '')
            gx, gy = (float(m.group(1)), float(m.group(2))) if m else (0, 0)
            ctx_chain.append(cls)
            kids = []
            for k in el: walk(k, cls if cls not in ('sc-l', 'sc-wcg', '') else ctx, 0, 0, kids)
            ctx_chain.pop()
            if not kids: return
            if gx or gy:
                x0 = min(k.x0 for k in kids) + gx; x1 = max(k.x1 for k in kids) + gx
                sink.append(Group(kids, tx=gx, ty=gy, x0=x0, x1=x1))
            else:
                sink.extend(kids)
            return
        st = style(cls, el, ctx)
        if st is None: return
        g = lambda k, d=0: float(el.get(k, d))
        if tag == 'rect': d = rect_d(g('x'), g('y'), g('width'), g('height'), g('rx'))
        elif tag == 'circle': d = circle_d(g('cx'), g('cy'), g('r'))
        elif tag == 'ellipse': d = ellipse_d(g('cx'), g('cy'), g('rx'), g('ry'))
        elif tag == 'path': d = el.get('d')
        else: return
        if st.get('dash'):
            d = dashes(points(d), *st['dash'])
        else:
            d = clean_path(d)
        x0, x1 = ext(d)
        if tag in ('circle', 'ellipse', 'rect') or not points(d):
            x0, x1 = (g('cx', g('x')) - g('r', g('rx', 0)) - 60, g('cx', g('x')) + g('r', g('rx', 0)) + g('width') + 60)
        if st.get('lock'):
            lk = c['lock']
            sink.append(Shape(d, grad=dict(type='linear', x1=-48, y1=0, x2=48, y2=86, stops=[(0, lk[0]), (0.5, lk[1]), (1, lk[2])]), x0=x0, x1=x1))
            return
        sink.append(Shape(d, fill=st.get('fill'), stroke=st.get('stroke'), sw=st.get('sw', 0), alpha=st.get('alpha', 1),
                          cap=st.get('cap'), join=st.get('join'), x0=x0 - st.get('sw', 0), x1=x1 + st.get('sw', 0)))

    walk(root, '', 0, 0, out)
    # the rider sits on the road in slide 2 (web: <image x=515 y=199.3 w=140 h=132 xMidYMax meet>)
    img = root.find('image')
    ix, iy, iw, ih = (float(img.get(k)) for k in ('x', 'y', 'width', 'height'))
    s = min(iw / RIDER_VB[0], ih / RIDER_VB[1])
    rx = ix + (iw - RIDER_VB[0] * s) / 2; ry = iy + ih - RIDER_VB[1] * s
    rider = Group([Group(rider_items(), sx=s, sy=s)], tx=rx, ty=ry, x0=rx, x1=rx + RIDER_VB[0] * s)
    return out, rider

POLY = re.compile(r'^M[-\d. ]+(L[-\d. ]+)+Z?$')

def clip_poly(pts, a, b):
    """Sutherland-Hodgman against the slab a <= x <= b."""
    for edge, keep in ((a, lambda x: x >= a), (b, lambda x: x <= b)):
        out = []
        for i, p in enumerate(pts):
            q = pts[i - 1]
            if keep(p[0]):
                if not keep(q[0]): out.append((edge, q[1] + (p[1] - q[1]) * (edge - q[0]) / (p[0] - q[0])))
                out.append(p)
            elif keep(q[0]):
                out.append((edge, q[1] + (p[1] - q[1]) * (edge - q[0]) / (p[0] - q[0])))
        pts = out
    return pts

def trim(sh, a, b):
    """Long ridge, road and lane paths: keep only the part inside [a, b] (plus a margin), so each slide piece stays small."""
    if not isinstance(sh, Shape) or sh.x1 - sh.x0 < 3 * PW: return sh
    m = 12 + sh.sw
    subs = [x for x in re.split(r'(?=M)', sh.d) if x]
    if not all(POLY.match(x) for x in subs): return sh
    out = []
    for sp in subs:
        pts = points(sp); closed = sp.endswith('Z')
        if closed:
            q = clip_poly(pts, a - m, b + m)
            if len(q) > 2: out.append('M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in q) + 'Z')
        else:
            keep = [i for i, (x, _) in enumerate(pts) if a - m <= x <= b + m]
            if not keep: continue
            lo, hi = max(0, keep[0] - 1), min(len(pts), keep[-1] + 2)
            if hi - lo > 1: out.append('M' + 'L'.join(f'{fmt(x)} {fmt(y)}' for x, y in pts[lo:hi]))
    return Shape(''.join(out), sh.fill, sh.stroke, sh.sw, sh.alpha, sh.cap, sh.join, sh.grad, a, b) if out else None

def merge(items):
    """Join neighbouring shapes that share one flat style into one path: fewer draw calls, same picture."""
    out = []
    for it in items:
        if isinstance(it, Group):
            out.append(Group(merge(it.items), it.tx, it.ty, it.sx, it.sy, it.px, it.py, it.x0, it.x1)); continue
        prev = out[-1] if out else None
        key = lambda s: (s.fill, s.stroke, s.sw, s.alpha, s.cap, s.join)
        if isinstance(prev, Shape) and not prev.grad and not it.grad and key(prev) == key(it):
            out[-1] = Shape(prev.d + it.d, it.fill, it.stroke, it.sw, it.alpha, it.cap, it.join, None, min(prev.x0, it.x0), max(prev.x1, it.x1))
        else:
            out.append(it)
    return out

def cut(items, a, b):
    """Keep only what reaches into scene x range [a, b], trimmed to it."""
    kept = [trim(it, a, b) for it in items if it.x1 >= a and it.x0 <= b]
    return [k for k in kept if k is not None]

# ---------- the Bluetooth badge disc: an uneven wash, lighter off-centre, deeper at the rim ----------
def bt_items():
    n, pts = 28, []
    for k in range(n):
        t = k / n * 2 * math.pi
        r = 64 * (1 + .04 * math.sin(t * 3 + 1) + .025 * math.sin(t * 5 + 2) + .018 * math.sin(t * 7 + .4))
        pts.append((80 + r * math.cos(t), 82 + r * math.sin(t)))
    d = f'M{fmt(pts[0][0])} {fmt(pts[0][1])}'
    for i in range(n):                                   # Catmull-Rom through the points, as cubic curves
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f'C{fmt(c1[0])} {fmt(c1[1])} {fmt(c2[0])} {fmt(c2[1])} {fmt(p2[0])} {fmt(p2[1])}'
    d += 'Z'
    return [Shape(d, grad=dict(type='radial', cx=72, cy=72, r=74, stops=[(0, '#D3909E'), (0.6, '#C67A8A'), (0.86, '#B0505F'), (1, '#8A2A40')]))]

# ---------- avatars: the web line drawings, flattened (circle clip, one transform) ----------
def avatar_vd(path):
    root = ET.parse(path).getroot()
    ns = '{http://www.w3.org/2000/svg}'
    items = []
    def num(e, k, d=0.0): return float(e.get(k, d))
    def to_d(e):
        t = e.tag.replace(ns, '')
        if t == 'rect': return rect_d(num(e, 'x'), num(e, 'y'), num(e, 'width'), num(e, 'height'), num(e, 'rx'))
        if t == 'circle': return circle_d(num(e, 'cx'), num(e, 'cy'), num(e, 'r'))
        if t == 'ellipse': return ellipse_d(num(e, 'cx'), num(e, 'cy'), num(e, 'rx'), num(e, 'ry'))
        if t == 'path': return clean_path(e.get('d'), 2)
    def walk(e, inh, sink):
        t = e.tag.replace(ns, '')
        if t in ('defs', 'clipPath'): return
        st = dict(inh)
        for k in ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'opacity'):
            if e.get(k) is not None: st[k] = e.get(k)
        if t in ('svg', 'g'):
            kids = []
            for k in e: walk(k, st, kids)
            tr = e.get('transform')
            if tr:
                # translate(a b) scale(s) translate(c d)  ->  one group: x' = a + s*(x + c)
                m = re.match(r'translate\(([-\d.]+)[ ,]+([-\d.]+)\)\s*scale\(([-\d.]+)\)\s*translate\(([-\d.]+)[ ,]+([-\d.]+)\)', tr)
                if m:
                    a, b, s, cc, dd = map(float, m.groups())
                    sink.append(Group([Group(kids, tx=cc, ty=dd)], tx=a, ty=b, sx=s, sy=s))
                    return
                m = re.match(r'translate\(([-\d.]+)[ ,]+([-\d.]+)\)$', tr)
                if m:
                    sink.append(Group(kids, tx=float(m.group(1)), ty=float(m.group(2)))); return
                raise ValueError(f'unhandled transform {tr} in {path}')
            sink.extend(kids); return
        d = to_d(e)
        if not d: return
        fill = st.get('fill', '#000')
        stroke = st.get('stroke') if st.get('stroke') not in (None, 'none') else None
        def col(v):
            if not v or v == 'none': return None
            v = v.strip()
            if v == '#fff': return '#FFFFFF'
            if re.fullmatch(r'#[0-9a-fA-F]{3}', v): return '#' + ''.join(ch * 2 for ch in v[1:]).upper()
            return v.upper()
        sink.append(Shape(d, fill=col(fill), stroke=col(stroke), sw=float(st.get('stroke-width', 1)) if stroke else 0,
                          alpha=float(st.get('opacity', 1)), cap=st.get('stroke-linecap'), join=st.get('stroke-linejoin')))
    walk(root, {}, items)
    return items

def main():
    sizes = {}
    # rider (replaces rider_street.webp; same 1050 x 990 frame so the paint overlays still line up)
    r = rider_items()
    write(os.path.join(ART, 'rider.svg'), svg_out(r, f'0 0 {RIDER_VB[0]} {RIDER_VB[1]}'))
    write(os.path.join(RES, 'rider_street.xml'), vd_out(r, *RIDER_VB, 350, 330))
    # scene: one source SVG per theme, five slide pieces per theme
    for theme in PALETTE:
        items, rider = scene_items(theme)
        # the full scene, rider included, for looking at; on the phone the rider is drawn on top from rider_street
        write(os.path.join(ART, f'scene_{theme}.svg'), svg_out(items + [rider], f'0 0 1950 {H}'))
        for i in range(5):
            a, b = i * PW, i * PW + PW + OVERLAP
            piece = [Group(merge(cut(items, a, b)), tx=-a)]
            write(os.path.join(RES, f'scene_{theme}_{i}.xml'),
                  vd_out(piece, PW + OVERLAP, H, PW + OVERLAP, H, clip=f'M0 0H{PW + OVERLAP}V{H}H0Z'))
            write(os.path.join(ART, 'pieces', f'scene_{theme}_{i}.svg'), svg_out(piece, f'0 0 {PW + OVERLAP} {H}'))
    bt = bt_items()
    write(os.path.join(ART, 'bt_disc.svg'), svg_out(bt, '0 0 160 160'))
    write(os.path.join(RES, 'bt_disc.xml'), vd_out(bt, 160, 160, 40, 40))
    for k in range(1, 12):
        av = avatar_vd(os.path.join(WEB, 'img', 'avatars', f'a{k}.svg'))
        write(os.path.join(RES, f'avatar_a{k}.xml'), vd_out(av, 100, 100, 64, 64, clip=circle_d(50, 50, 50)))
    for f in sorted(os.listdir(RES)):
        if f.startswith(('scene_', 'rider_street', 'bt_disc', 'avatar_a')):
            sizes[f] = os.path.getsize(os.path.join(RES, f))
    for f, s in sizes.items(): print(f'{s:>7}  {f}')
    print(f'{sum(sizes.values()):>7}  total')

if __name__ == '__main__':
    main()
