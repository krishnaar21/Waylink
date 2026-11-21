"""One-off: trace the old rider picture (1050 x 990 px, transparent) into android/art/onb/rider_trace.svg.

Already done; rerun only if the rider artwork changes. Needs `pip install vtracer` and Node (`npx svgo@3`).
    python3 android/tools/trace_rider.py path/to/rider.png
Steps: snap every pixel to five inks (black, paper white, light grey, mid grey, red), smooth with a 5 px mode
filter, trace the ink layers as stacked splines (speckles under 16 px dropped), let SVGO round to 0.1 px, snap
fills back to the five inks. The soft ground shadow is not traced; onb_art.py draws it as one squashed radial fade.
"""
import os, re, subprocess, sys, tempfile
import numpy as np
from PIL import Image, ImageFilter
import vtracer

INKS = [('#0A0A0A', (4, 4, 4)), ('#ECEBEB', (236, 235, 235)), ('#A8A6A6', (168, 166, 166)), ('#686666', (104, 102, 102)), ('#B5263A', (186, 36, 52))]
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'art', 'onb', 'rider_trace.svg')

def main(src):
    a = np.array(Image.open(src).convert('RGBA')).astype(float)
    rgb = a[..., :3]; pal = np.array([c for _, c in INKS], float)
    lab = ((rgb[:, :, None] - pal[None, None]) ** 2).sum(-1).argmin(-1)
    lab[((rgb.max(-1) - rgb.min(-1)) > 45) & (rgb[..., 0] > rgb[..., 1] + 30)] = 4
    lab[a[..., 3] < 236] = 255                                   # the shadow (alpha < 236) is left out
    lab = np.array(Image.fromarray(lab.astype(np.uint8)).filter(ImageFilter.ModeFilter(5)))
    o = np.zeros(a.shape, np.uint8)
    for i, (_, c) in enumerate(INKS): o[lab == i, :3] = c; o[lab == i, 3] = 255
    with tempfile.TemporaryDirectory() as t:
        png, svg, opt = (os.path.join(t, n) for n in ('q.png', 'q.svg', 'o.svg'))
        Image.fromarray(o).save(png)
        vtracer.convert_image_to_svg_py(png, svg, colormode='color', hierarchical='stacked', mode='spline', filter_speckle=16,
                                        color_precision=8, layer_difference=8, corner_threshold=60, length_threshold=5.0,
                                        max_iterations=10, splice_threshold=45, path_precision=1)
        s = re.sub(r'width="(\d+)" height="(\d+)"', r'viewBox="0 0 \1 \2"', open(svg).read())
        open(svg, 'w').write(s)
        subprocess.run(['npx', '-y', 'svgo@3', '--multipass', '-p', '1', '-i', svg, '-o', opt], check=True)
        s = open(opt).read()
    def snap(m):
        h = m.group(1); r, g, b = (int(h[i:i + 2], 16) for i in (1, 3, 5))
        if r - g > 50: return 'fill="#B5263A"'
        return 'fill="%s"' % min(INKS, key=lambda p: sum((p[1][k] - v) ** 2 for k, v in enumerate((r, g, b))))[0]
    open(OUT, 'w').write(re.sub(r'fill="(#[0-9A-Fa-f]{6})"', snap, s))
    print('wrote', OUT)

if __name__ == '__main__':
    main(sys.argv[1])
