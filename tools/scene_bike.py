# The calls slide's bike is baked into the scene pictures. Rebuild it from the coloured street bike
# (posterised grey, wine kept) and paint it over the old one in every scene. Position comes from tools/onb_scene.py.
import math, numpy as np
from PIL import Image, ImageFilter
def road_y(x): return 318 + math.sin(x / 260) * 16 + math.sin(x / 90) * 4
src = Image.open('img/onb/rider-street.webp').convert('RGBA')
a = np.asarray(src).astype(np.float32) / 255; rgb, al = a[..., :3], a[..., 3:]
hsv = np.asarray(Image.fromarray((rgb * 255).astype(np.uint8)).convert('HSV')).astype(np.float32) / 255
lum = rgb @ np.array([.299, .587, .114], np.float32)
grey = (np.round(lum * 4) / 4)[..., None].repeat(3, 2)          # posterised like the old mono bike
keep = np.clip((hsv[..., 1] - .2) / .2, 0, 1)[..., None]        # wine stays wine
mono = Image.fromarray((np.dstack([grey * (1 - keep) + rgb * keep, al]) * 255).astype(np.uint8), 'RGBA')
mono.resize((300, 283), Image.LANCZOS).save('img/onb/rider-mono.webp', quality=86, method=6)
cx = 585; W, H = 140, 132; x0, y0 = cx - W // 2, round(road_y(cx) - H)
bike = mono.resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(.5))
bike.putalpha(bike.getchannel('A').point(lambda p: int(p * .95)))
for n in ['light', 'dark', 'night']:
    p = f'img/onb/scene-{n}.webp'; sc = Image.open(p).convert('RGBA')
    sc.alpha_composite(bike, (x0, y0)); sc.convert('RGB').save(p, quality=62, method=6); print('painted', p)
