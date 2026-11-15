# Recolour the blue in baked onboarding art to wine red, keeping each pixel's shading and grain.
import sys, numpy as np
from PIL import Image
def rewine(path):
    im = Image.open(path); has_a = im.mode == 'RGBA'
    a = np.asarray(im.convert('RGBA')).astype(np.float32) / 255
    rgb = a[..., :3]
    hsv = np.asarray(Image.fromarray((rgb * 255).astype(np.uint8)).convert('HSV')).astype(np.float32) / 255
    h, s, v = hsv[..., 0] * 360, hsv[..., 1], hsv[..., 2]
    blue = np.clip(1 - np.abs(h - 225) / 55, 0, 1) * np.clip((s - .06) / .14, 0, 1)
    # wine: hue ~352°, slightly deeper value, a touch less saturated for the paper look
    nh = np.full_like(h, 352 / 360); ns = np.clip(s * .92, 0, 1); nv = np.clip(v * .86, 0, 1)
    new = np.asarray(Image.fromarray(np.dstack([nh, ns, nv]).__mul__(255).astype(np.uint8), 'HSV').convert('RGB')).astype(np.float32) / 255
    out = rgb * (1 - blue[..., None]) + new * blue[..., None]
    res = np.dstack([out, a[..., 3:]]) if has_a else out
    Image.fromarray((res * 255).round().astype(np.uint8), 'RGBA' if has_a else 'RGB').save(path, quality=62 if not has_a else 80, method=6)
for p in sys.argv[1:]: rewine(p); print('rewined', p)
