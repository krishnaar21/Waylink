# Wine accents on the street bike: the yellow helmet and indicator become wine red,
# and the white tank and tail panels pick up a wine stripe. Shading and alpha are kept.
import numpy as np
from PIL import Image
src = Image.open('img/onb/rider-street.webp').convert('RGBA')
a = np.asarray(src).astype(np.float32) / 255; rgb, al = a[..., :3], a[..., 3:]
hsv = np.asarray(Image.fromarray((rgb * 255).astype(np.uint8)).convert('HSV')).astype(np.float32) / 255
h, s, v = hsv[..., 0] * 360, hsv[..., 1], hsv[..., 2]
WINE = np.array([0xA8, 0x16, 0x2A], np.float32) / 255
# 1. yellow → wine, keeping light and dark through value
yel = np.clip((1 - np.abs(h - 52) / 32) * 2, 0, 1) * np.clip((s - .15) / .12, 0, 1)
wine_y = np.clip(WINE * (.5 + .75 * v)[..., None], 0, 1)
out = rgb * (1 - yel[..., None]) + wine_y * yel[..., None]
# 2. whole white panels on the tank, side cover and tail cowl → wine (picked by shape, so each fills edge to edge)
from scipy import ndimage
white = (v > .62) & (s < .25) & (al[..., 0] > .5)
lab, _ = ndimage.label(white)
PANELS = [(200, 141), (208, 163), (74, 148), (94, 149)]  # a point inside each panel, image pixels
pick = np.isin(lab, [lab[y, x] for x, y in PANELS if lab[y, x]])
pick = ndimage.binary_dilation(pick, iterations=2)  # take in the soft anti-aliased rim
w = (pick * np.clip((v - .28) / .34, 0, 1))[..., None]
panel = np.clip(WINE * (.62 + .5 * v[..., None]), 0, 1)
out = out * (1 - w) + panel * w
Image.fromarray((np.dstack([out, al]) * 255).round().astype(np.uint8), 'RGBA').save('img/onb/rider-street.webp', quality=86, method=6)
print('ok')
