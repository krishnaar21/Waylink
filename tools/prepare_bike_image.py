"""Turn a bike photo into an app-ready model image.

Usage:  python3 tools/prepare_bike_image.py <photo> <model-id> [--keep-bg]
Model ids: ns200 ns400z ns160 ns125 n250 n160 pulsar150 pulsar220f dominar400 dominar250

- A PNG with a transparent background is cropped tight and kept transparent (best result).
- A photo on a plain white or light studio background has that background removed,
  unless you pass --keep-bg.
- Output: img/bikes/<model-id>.webp, 900 px on the long side, side-on bikes look best.
The app picks it up from img/bikes/manifest.json.
Needs: pip install pillow numpy scipy
"""
import sys, os
import numpy as np
from PIL import Image

def main():
    if len(sys.argv) < 3:
        print(__doc__); sys.exit(1)
    src, mid = sys.argv[1], sys.argv[2]
    keep = '--keep-bg' in sys.argv
    im = Image.open(src).convert('RGBA')
    a = np.asarray(im).copy()
    if not keep and (a[..., 3] == 255).all():
        from scipy import ndimage
        rgb = a[..., :3].astype(np.float32)
        light = rgb.min(axis=2) >= 238
        lab, n = ndimage.label(light)
        edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
        bg = np.isin(lab, list(edge))
        band = ndimage.binary_dilation(bg, iterations=2) & ~bg
        alpha = np.full(light.shape, 255, np.float32)
        alpha[bg] = 0
        alpha[band] = np.clip((255 - rgb.min(axis=2)[band]) * 3, 0, 255)
        a[..., 3] = alpha.astype(np.uint8)
    al = a[..., 3]; al[al >= 240] = 255
    out = Image.fromarray(a, 'RGBA')
    out = out.crop(out.getbbox())
    out.thumbnail((900, 900), Image.LANCZOS)
    os.makedirs('img/bikes', exist_ok=True)
    dst = f'img/bikes/{mid}.webp'
    out.save(dst, 'WEBP', quality=84, method=6)
    import json
    mf = 'img/bikes/manifest.json'
    m = json.load(open(mf)) if os.path.exists(mf) else {}
    m[mid] = {'src': dst, 'fit': 'contain', 'credit': input('Credit line (e.g. "Photo: Name, CC BY 4.0"): ').strip() or 'Your photo'}
    json.dump(m, open(mf, 'w'), indent=2, ensure_ascii=False)
    print(f'Saved {dst} ({out.width}×{out.height}) and updated {mf}.')

if __name__ == '__main__':
    main()
