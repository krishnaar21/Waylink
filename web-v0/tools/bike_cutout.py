# Gentle cut-out: only the white backdrop and the ground shadow go; every part of the bike stays.
import cv2, numpy as np, sys
from PIL import Image
def cut(src, out):
    im = np.array(Image.open(src).convert('RGBA')).astype(np.float32)
    rgb = im[..., :3]; a = im[..., 3].copy(); h, w = a.shape
    mn = rgb.min(2); sat = rgb.max(2) - mn
    solid = a > 250
    ys = np.where((solid & ~((mn > 238) & (sat < 14))).any(1))[0]; ytop, ybot = ys.min(), ys.max()
    band = np.zeros((h, w), bool); band[int(ybot - (ybot - ytop) * 0.21):, :] = True
    if (~solid).mean() > 0.2:
        # already transparent: drop the soft shadow (half-transparent) along the ground only
        a[band & (a < 250)] = 0
    else:
        white = (mn > 249) & (sat < 8)
        # shadow: grey, unsaturated, in the ground band, touching the backdrop
        band2 = np.zeros((h, w), bool); band2[int(ybot - (ybot - ytop) * 0.26):, :] = True
        shadow = (band & (sat < 24) & (mn > 60)) | (band2 & (sat < 14) & (mn > 185))
        cand = (white | shadow).astype(np.uint8)
        n, lab = cv2.connectedComponents(cand, connectivity=4)
        border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
        bg = np.isin(lab, list(border))
        # holes between spokes etc: big enclosed pure-white areas
        holes = (((mn > 240) & (sat < 10)) | (band & (mn > 222) & (sat < 16))) & ~bg
        n2, lab2, st2, _ = cv2.connectedComponentsWithStats(holes.astype(np.uint8), 4)
        for i in range(1, n2):
            if st2[i, cv2.CC_STAT_AREA] > 60: bg |= lab2 == i
        a = np.where(bg, 0, 255).astype(np.float32)
        # soft edge: un-mix from white on the 1px rim so nothing looks cut out
        rim = cv2.dilate(bg.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & ~bg
        soft = np.clip((255 - mn) / 150 * 255, 0, 255)
        a[rim] = np.maximum(soft[rim], 60)
        sa = np.maximum(a[..., None] / 255, 1e-3)
        fix = rim[..., None]
        rgb = np.where(fix, np.clip((rgb - (1 - sa) * 255) / sa, 0, 255), rgb)
    # thin ground streaks left of the tyres: anything only a few px tall in the ground band goes
    m = (a > 40).astype(np.uint8)
    tall = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((17, 1), np.uint8))
    tall = cv2.dilate(tall, np.ones((3, 3), np.uint8))
    a = np.where(band & (tall == 0), 0, a)
    a = np.where(a < 16, 0, a)
    out_im = np.dstack([rgb, a]).astype(np.uint8)
    ys, xs = np.where(a > 10); pad = 8
    y0, y1, x0, x1 = max(0, ys.min() - pad), min(h, ys.max() + pad), max(0, xs.min() - pad), min(w, xs.max() + pad)
    Image.fromarray(out_im[y0:y1, x0:x1]).save(out)
for s_, o in zip(sys.argv[1::2], sys.argv[2::2]): cut(s_, o)
