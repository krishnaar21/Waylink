# Wine accents on the street bike: the yellow helmet and indicator become wine red,
# and the white tank and tail panels pick up a wine stripe. Shading and alpha are kept.
import numpy as np
from PIL import Image
src = Image.open('img/onb/rider-street.webp').convert('RGBA')
a = np.asarray(src).astype(np.float32) / 255; rgb, al = a[..., :3], a[..., 3:]
hsv = np.asarray(Image.fromarray((rgb * 255).astype(np.uint8)).convert('HSV')).astype(np.float32) / 255
h, s, v = hsv[..., 0] * 360, hsv[..., 1], hsv[..., 2]
WINE = np.array([0xA8, 0x16, 0x2A], np.float32) / 255
# 1. yellow → wine. Each pixel is grey (its blue channel) plus some yellow (min(R,G) − B);
#    swapping only the yellow part for wine keeps the helmet's gradient into white and black intact.
grey = rgb[..., 2:3]
yel = np.clip(np.minimum(rgb[..., 0], rgb[..., 1]) - rgb[..., 2], 0, 1)
yel = np.clip(np.where(yel < .06, 0, yel) * 1.6, 0, 1)[..., None]  # leave near-neutral blacks and whites alone
out = np.clip(grey * (1 - yel) + yel * (grey * .15 + WINE * .95), 0, 1)
# 2. whole white panels on the tank, side cover and tail cowl → wine (picked by shape, so each fills edge to edge)
from scipy import ndimage
white = (v > .62) & (s < .25) & (al[..., 0] > .5)
lab, _ = ndimage.label(white)
PANELS = [(200, 141), (208, 163), (74, 148), (94, 149)]  # a point inside each panel, image pixels
pick = np.isin(lab, [lab[y, x] for x, y in PANELS if lab[y, x]])
# soft mask: grow a touch, then feather, so the wine melts into the black outlines like the helmet
pick = ndimage.gaussian_filter(ndimage.binary_dilation(pick, iterations=1).astype(np.float32), 1.1)
w = (np.clip(pick * 1.4, 0, 1) * np.clip((v - .22) / .4, 0, 1))[..., None]
# shade each panel like the helmet: deep wine at its edges, lighter inside, a pale sheen along its top
core = np.isin(lab, [lab[y, x] for x, y in PANELS if lab[y, x]])
depth = np.clip(ndimage.distance_transform_edt(core) / 4.5, 0, 1)
rel = np.zeros_like(v)
for x, y in PANELS:
    k = lab[y, x]
    if not k: continue
    ys = np.where(lab == k)[0]; m = lab == k
    rel[m] = (np.where(m)[0] - ys.min()) / max(1, ys.max() - ys.min())
rel = ndimage.gaussian_filter(rel, 1.2)
tint = np.clip(WINE * (.62 + .5 * depth)[..., None], 0, 1)
sheen = (np.clip(1 - rel * 2.6, 0, 1) * depth * .45)[..., None]
panel = tint * (1 - sheen) + np.array([.98, .9, .9]) * sheen
panel = panel * (.75 + .25 * v[..., None])
out = out * (1 - w) + panel * w
Image.fromarray((np.dstack([out, al]) * 255).round().astype(np.uint8), 'RGBA').save('img/onb/rider-street.webp', quality=86, method=6)
print('ok')
