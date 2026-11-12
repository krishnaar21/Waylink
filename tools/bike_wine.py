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
# 2. white panels on the tank and the tail cowl → wine
H, W = v.shape; yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
ovals = [(206, 146, 30, 17), (84, 150, 24, 10)]  # tank, tail cowl: centre x, y and radii in image pixels
region = np.zeros_like(v)
for cx, cy, rx, ry in ovals: region = np.maximum(region, np.clip(2.2 - 2.2 * np.hypot((xx - cx) / rx, (yy - cy) / ry), 0, 1))
white = np.clip((v - .62) / .2, 0, 1) * np.clip((.22 - s) / .12, 0, 1) * region
panel = np.clip(WINE * (.7 + .4 * v[..., None]), 0, 1)
out = out * (1 - white[..., None]) + panel * white[..., None]
Image.fromarray((np.dstack([out, al]) * 255).round().astype(np.uint8), 'RGBA').save('img/onb/rider-street.webp', quality=86, method=6)
print('ok')
