"""Fetch openly licensed photos of every Kickstart bike model from Wikimedia Commons.

Run from the app folder on a computer with internet:
    pip install pillow
    python3 tools/fetch_bike_photos.py            # fetch any model that has no photo yet
    python3 tools/fetch_bike_photos.py --list     # show the top candidates per model, download nothing
    python3 tools/fetch_bike_photos.py --force    # refetch every model
    python3 tools/fetch_bike_photos.py --pick ns400z "File:Bajaj Pulsar NS400Z.jpg"   # use a specific file

Only files under CC0, public domain, CC BY or CC BY-SA are used. Each photo's author, licence and
source page go into img/bikes/manifest.json, and the app shows them under Settings → Photo credits.
CC BY-SA photos stay under CC BY-SA after cropping; the credit line names the licence.
"""
import io, json, re, sys, html, urllib.parse, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'img' / 'bikes'
MANIFEST = OUT / 'manifest.json'
API = 'https://commons.wikimedia.org/w/api.php'
UA = 'KickstartBikeApp/1.0 (photo credits kept in-app; contact: app owner)'

MODELS = {
    'ns200': ['Bajaj Pulsar NS200', 'Pulsar 200NS', 'Pulsar NS 200'],
    'ns400z': ['Bajaj Pulsar NS400Z', 'Pulsar NS400Z'],
    'ns160': ['Bajaj Pulsar NS160', 'Pulsar NS 160'],
    'ns125': ['Bajaj Pulsar NS125', 'Pulsar NS 125'],
    'n250': ['Bajaj Pulsar N250', 'Bajaj Pulsar F250'],
    'n160': ['Bajaj Pulsar N160', 'Pulsar N160'],
    'pulsar150': ['Bajaj Pulsar 150', 'Pulsar 150'],
    'pulsar220f': ['Bajaj Pulsar 220F', 'Pulsar 220'],
    'dominar400': ['Bajaj Dominar 400', 'Dominar 400'],
    'dominar250': ['Bajaj Dominar 250', 'Dominar 250'],
}
FREE = re.compile(r'^(CC0|Public domain|PD.*|CC BY(-SA)? [0-9.]+.*|CC BY(-SA)?)$', re.I)

def get(params):
    url = API + '?' + urllib.parse.urlencode({**params, 'format': 'json', 'formatversion': 2})
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=30) as r:
        return json.load(r)

def strip(s):
    return html.unescape(re.sub(r'<[^>]+>', '', s or '')).strip()

def candidates(queries):
    seen, out = set(), []
    for q in queries:
        data = get({'action': 'query', 'generator': 'search', 'gsrsearch': f'filetype:bitmap {q}', 'gsrnamespace': 6, 'gsrlimit': 25,
                    'prop': 'imageinfo', 'iiprop': 'url|size|extmetadata', 'iiurlwidth': 1600})
        for p in (data.get('query', {}).get('pages') or []):
            if p['title'] in seen or not p.get('imageinfo'):
                continue
            seen.add(p['title'])
            ii = p['imageinfo'][0]; md = ii.get('extmetadata', {})
            lic = strip(md.get('LicenseShortName', {}).get('value'))
            w, h = ii.get('width', 0), ii.get('height', 0)
            if not FREE.match(lic) or w < 900 or w / max(h, 1) < 1.15:
                continue
            title = p['title'].lower()
            score = sum(tok.lower() in title for tok in q.split()) * 3 + (2 if 'side' in title else 0) + min(w, 4000) / 2000
            out.append({'title': p['title'], 'thumb': ii.get('thumburl') or ii['url'], 'page': ii.get('descriptionurl'), 'license': lic,
                        'author': strip(md.get('Artist', {}).get('value')) or 'Unknown author', 'w': w, 'h': h, 'score': score})
    return sorted(out, key=lambda c: -c['score'])

def by_title(title):
    data = get({'action': 'query', 'titles': title, 'prop': 'imageinfo', 'iiprop': 'url|size|extmetadata', 'iiurlwidth': 1600})
    p = data['query']['pages'][0]; ii = p['imageinfo'][0]; md = ii.get('extmetadata', {})
    lic = strip(md.get('LicenseShortName', {}).get('value'))
    if not FREE.match(lic):
        sys.exit(f'{title} is licensed "{lic}", which is not free to reuse here.')
    return {'title': p['title'], 'thumb': ii.get('thumburl') or ii['url'], 'page': ii.get('descriptionurl'), 'license': lic,
            'author': strip(md.get('Artist', {}).get('value')) or 'Unknown author'}

def save(mid, c, manifest):
    from PIL import Image
    req = urllib.request.Request(c['thumb'], headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        im = Image.open(io.BytesIO(r.read())).convert('RGB')
    tw, th = 1200, 750  # 16:10, the app's picture frame
    sc = max(tw / im.width, th / im.height)
    im = im.resize((round(im.width * sc), round(im.height * sc)), Image.LANCZOS)
    left, top = (im.width - tw) // 2, (im.height - th) // 2
    im.crop((left, top, left + tw, top + th)).save(OUT / f'{mid}.webp', 'WEBP', quality=82, method=6)
    manifest[mid] = {'src': f'img/bikes/{mid}.webp', 'fit': 'cover', 'credit': f"Photo: {c['author']}, {c['license']}, via Wikimedia Commons (cropped)",
                     'license': c['license'], 'author': c['author'], 'source': c['page'], 'title': c['title']}
    print(f'  ✓ {mid}: {c["title"]} — {c["author"]} ({c["license"]})')

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {}
    args = sys.argv[1:]
    if '--pick' in args:
        i = args.index('--pick'); mid, title = args[i + 1], args[i + 2]
        save(mid, by_title(title), manifest)
    else:
        force, listing = '--force' in args, '--list' in args
        for mid, queries in MODELS.items():
            if not force and not listing and manifest.get(mid, {}).get('fit') == 'cover':
                print(f'  · {mid}: already has a photo'); continue
            cands = candidates(queries)
            if listing:
                print(f'\n{mid}:'); [print(f'   {c["title"]}  [{c["license"]}]  {c["page"]}') for c in cands[:5]]; continue
            if not cands:
                print(f'  ✗ {mid}: no freely licensed landscape photo found. Try --pick with a file you like.'); continue
            save(mid, cands[0], manifest)
    if '--list' not in args:
        MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + '\n')
        print(f'\nSaved {MANIFEST.relative_to(ROOT)}. Check each photo shows the right bike before publishing.')

if __name__ == '__main__':
    main()
