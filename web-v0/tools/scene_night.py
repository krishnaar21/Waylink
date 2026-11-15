# Paper dark onboarding art: recolour the navy night scene to warm lamplight tones,
# keeping saturated bits (the blue turn sign, accents, the green call dot) as they are.
import numpy as np
from PIL import Image
src = np.asarray(Image.open('img/onb/scene-dark.webp').convert('RGB')).astype(np.float32) / 255
mx, mn = src.max(2), src.min(2)
sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
lum = src @ np.array([.2126, .7152, .0722], np.float32)
# Warm ramp from the page colour up to paper white (Paper dark: bg #1E1B17, ink #EDE5D6).
lo, hi = np.array([0x1A, 0x17, 0x13]) / 255, np.array([0xED, 0xE5, 0xD6]) / 255
t = np.clip((lum - .02) / .9, 0, 1) ** .9
warm = lo + (hi - lo) * t[..., None]
# Keep strongly coloured, reasonably bright pixels (sign, accents); recolour the navy wash.
keep = np.clip((sat - .45) / .2, 0, 1) * np.clip((mx - .3) / .15, 0, 1)
out = warm * (1 - keep[..., None]) + src * keep[..., None]
Image.fromarray((out * 255).round().astype(np.uint8)).save('img/onb/scene-night.webp', quality=62, method=6)
print('ok')
