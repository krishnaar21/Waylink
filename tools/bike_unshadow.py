# Drop the soft ground shadow from a transparent illustration, then trim and pad.
import cv2, numpy as np, sys
from PIL import Image
def clean(src, out, W=1000):
    im = Image.open(src).convert('RGBA')
    a = np.array(im).astype(np.int16); rgb = a[..., :3]; al = a[..., 3].copy(); h, w = al.shape
    mn = rgb.min(2); sat = rgb.max(2) - mn
    ys = np.where((al > 200).any(1))[0]; ytop, yb = ys.min(), ys.max(); H = yb - ytop
    band = np.zeros((h, w), bool); band[int(yb - H * 0.16):, :] = True
    # the shadow is a smooth, flat grey: low colour, low local texture, and big
    g = mn.astype(np.float32)
    mu = cv2.blur(g, (9, 9)); sd = np.sqrt(np.maximum(cv2.blur(g * g, (9, 9)) - mu * mu, 0))
    cand = (band & (sat < 22) & (mn > 90) & (sd < 7)).astype(np.uint8)
    n0, lab0, st0, _ = cv2.connectedComponentsWithStats(cand, 8)
    shadow = np.zeros((h, w), bool)
    for i in range(1, n0):
        if st0[i, cv2.CC_STAT_AREA] > h * w * 0.0015: shadow |= lab0 == i
    shadow = cv2.dilate(shadow.astype(np.uint8), np.ones((5, 5), np.uint8)).astype(bool) & band & (sat < 26) & (mn > 70)
    shadow |= band & (al < 120)
    al[shadow] = 0
    # remove what is left of the shadow: thin, wide streaks in the ground band
    m = (al > 40).astype(np.uint8)
    tall = cv2.dilate(cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((15, 1), np.uint8)), np.ones((3, 3), np.uint8))
    al[band & (tall == 0)] = 0
    # keep only blobs connected to the bike
    n, lab, st, _ = cv2.connectedComponentsWithStats((al > 40).astype(np.uint8), 8)
    big = st[1:, cv2.CC_STAT_AREA].max()
    for i in range(1, n):
        if st[i, cv2.CC_STAT_AREA] < big * 0.002: al[lab == i] = 0
    a[..., 3] = al
    o = Image.fromarray(a.astype(np.uint8))
    bb = o.getchannel('A').point(lambda v: 255 if v > 24 else 0).getbbox(); o = o.crop(bb)
    if o.width > W: o = o.resize((W, round(o.height * W / o.width)), Image.LANCZOS)
    p = round(max(o.size) * 0.04); c = Image.new('RGBA', (o.width + 2 * p, o.height + 2 * p), (0, 0, 0, 0)); c.paste(o, (p, p))
    c.save(out, quality=90, method=6)
for s_, o in zip(sys.argv[1::2], sys.argv[2::2]): clean(s_, o)
