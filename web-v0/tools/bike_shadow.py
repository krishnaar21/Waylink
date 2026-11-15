# Soft ground shadow for the street bike: no pale rim or leftover light patch, edges fade into any background.
import numpy as np
from PIL import Image
im = Image.open('img/onb/rider-street.webp').convert('RGBA')
a = np.asarray(im).astype(np.float32) / 255; rgb, al = a[..., :3], a[..., 3]
H, W = al.shape; yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
lum = rgb @ np.array([.299, .587, .114], np.float32)
cx, cy, rx, ry = 175, 318, 170, 11
d = np.hypot((xx - cx) / rx, (yy - cy) / ry)
tyres = ((xx >= 36) & (xx <= 109)) | ((xx >= 238) & (xx <= 299))
ground = (yy >= 312) | ((yy >= 300) & ~tyres)                                     # below the tyres' contact: shadow only
# white-matte fringe: semi-transparent edge pixels were blended on white; take the white back out
fr = (al > .02) & (al < .97)
rgb[fr] = np.clip((rgb[fr] - (1 - al[fr])[:, None]) / al[fr][:, None], 0, 1)
# pale leftovers in the shadow band (the old white backdrop's rim) go dark
pale = ground & (lum > .18)
rgb[pale] = rgb[pale] * .15
# the shadow itself: soft ellipse, darkest under the bike, fading out at the ends and the bottom
soft = np.clip((1 - d) / .5, 0, 1) ** 1.1 * .85
al = np.where(ground, np.minimum(al, soft), al)
# side ends of the ellipse above the contact line, outside the wheels, fade the same way
sides = (yy >= 300) & ((xx < 34) | (xx > 322))
al = np.where(sides, np.minimum(al, soft), al)
rgb = np.where(ground[..., None], rgb * .2, rgb)        # shadow is plain dark, no grey streaks
Image.fromarray((np.dstack([rgb, al]) * 255).round().astype(np.uint8), 'RGBA').save('img/onb/rider-street.webp', quality=86, method=6)
print('ok')
