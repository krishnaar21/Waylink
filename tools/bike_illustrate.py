# Turn a cut-out photo into a flat, vector-style illustration (like the NS200 artwork):
# smooth surfaces, a limited palette, clean ink outlines.
import cv2, numpy as np, sys
from PIL import Image
def illus(src, out, k=22, W=900):
    im = Image.open(src).convert('RGBA')
    if im.width > W: im = im.resize((W, int(im.height * W / im.width)), Image.LANCZOS)
    a = np.array(im); rgb = cv2.cvtColor(a[..., :3], cv2.COLOR_RGB2BGR); al = a[..., 3]
    m = al > 8
    sm = rgb.copy()
    for _ in range(4): sm = cv2.bilateralFilter(sm, 7, 40, 7)
    # palette in Lab space
    lab = cv2.cvtColor(sm, cv2.COLOR_BGR2LAB).reshape(-1, 3).astype(np.float32)
    pts = lab[m.reshape(-1)]
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 30, 0.5)
    _, lbl, ctr = cv2.kmeans(pts, k, None, crit, 3, cv2.KMEANS_PP_CENTERS)
    q = lab.copy(); q[m.reshape(-1)] = ctr[lbl.flatten()]
    q = cv2.cvtColor(q.reshape(sm.shape).astype(np.uint8), cv2.COLOR_LAB2BGR)
    q = cv2.medianBlur(q, 3)
    # gentle pop: a little more contrast and colour
    hsv = cv2.cvtColor(q, cv2.COLOR_BGR2HSV).astype(np.float32)
    hsv[..., 1] = np.clip(hsv[..., 1] * 1.12, 0, 255); hsv[..., 2] = np.clip((hsv[..., 2] - 128) * 1.06 + 132, 0, 255)
    q = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)
    # ink lines from colour-region edges, thin and soft
    g = cv2.cvtColor(sm, cv2.COLOR_BGR2GRAY)
    ed = cv2.Canny(g, 60, 140)
    ed = cv2.GaussianBlur(ed.astype(np.float32), (3, 3), 0) / 255
    ink = np.array([24, 22, 26], np.float32)
    qf = q.astype(np.float32)
    qf = qf * (1 - ed[..., None] * .55) + ink * (ed[..., None] * .55)
    # silhouette outline
    mm = (al > 100).astype(np.uint8)
    rim = cv2.morphologyEx(mm, cv2.MORPH_GRADIENT, np.ones((3, 3), np.uint8)).astype(bool)
    qf[rim] = qf[rim] * .35 + ink * .65
    o = cv2.cvtColor(np.clip(qf, 0, 255).astype(np.uint8), cv2.COLOR_BGR2RGB)
    res = Image.fromarray(np.dstack([o, al]))
    # breathing room so nothing ever sits flush with the edge
    p = int(max(res.size) * 0.04); canvas = Image.new('RGBA', (res.width + 2 * p, res.height + 2 * p), (0, 0, 0, 0)); canvas.paste(res, (p, p))
    canvas.save(out, quality=88, method=6)
for s_, o in zip(sys.argv[1::2], sys.argv[2::2]): illus(s_, o)
