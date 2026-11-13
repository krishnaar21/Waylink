# Watercolour disc behind the Bluetooth badge: an uneven wash with darker pooled edges,
# a lighter bloom inside and paper granulation. Transparent outside the wash.
import numpy as np
from PIL import Image, ImageFilter
N, rng = 240, np.random.default_rng(11)
y, x = np.mgrid[0:N, 0:N].astype(np.float32); cx = cy = N / 2
def noise(scale, seed):
    g = np.random.default_rng(seed).random((N // scale + 2, N // scale + 2)).astype(np.float32)
    return np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC)).astype(np.float32) / 255
ang = np.arctan2(y - cy, x - cx); r = np.hypot(x - cx, y - cy)
# wobbly edge: radius varies with angle and low-frequency noise
edge = N * .40 * (1 + .04 * np.sin(ang * 3 + 1) + .025 * np.sin(ang * 5 + 2)) + (noise(30, 3) - .5) * 10
inside = np.clip((edge - r) / 3.0, 0, 1)
# pigment: pooled ring at the edge, lighter bloom off-centre, blotches and fine granulation
d = np.clip(edge - r, 0, None)
pool = np.exp(-d / 5) * .6
bloom = np.exp(-(((x - cx + 18) ** 2 + (y - cy + 22) ** 2) / (2 * 38 ** 2))) * .38
blot = (noise(40, 5) - .5) * .5 + (noise(14, 8) - .5) * .25
gran = (rng.random((N, N)).astype(np.float32) - .5) * .22
dens = np.clip(.5 + pool * 1.5 - bloom * 1.9 + blot * 1.1 + gran * 1.2, 0, 1)
light, deep = np.array([0x8F, 0xB2, 0xF5], np.float32) / 255, np.array([0x1E, 0x3F, 0xB0], np.float32) / 255  # wash to pooled ultramarine
rgb = light + (deep - light) * dens[..., None]
alpha = inside * np.clip(.82 + dens * .25, 0, 1)
img = Image.fromarray(np.dstack([rgb, alpha]).clip(0, 1).__mul__(255).astype(np.uint8), 'RGBA')
img = img.filter(ImageFilter.GaussianBlur(.3))
img.resize((160, 160), Image.LANCZOS).save('img/onb/bt-disc.webp', quality=80, method=6)
print('ok')
