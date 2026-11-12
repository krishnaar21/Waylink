# Run from the repo root. Needs: Iconify's json/hugeicons.json, and the UIcons regular-rounded CSS and WOFF from freepik-company/flaticon-uicons.
# Waylink icon keys -> Hugeicons (free, MIT; via Iconify's open data) and UIcons Regular Rounded (Flaticon licence, credit required).
import json, re, os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
HUGE = json.load(open(os.environ.get('HUGE_JSON', '/tmp/hugeicons.json')))['icons']
css = open(os.environ.get('UICONS_CSS', '/tmp/uicons-rr.css')).read()
UMAP = {m.group(1): int(m.group(2), 16) for m in re.finditer(r'\.fi-rr-([a-z0-9-]+):before\s*\{\s*content:\s*"\\([0-9a-fA-F]+)"', css)}
font = TTFont(os.environ.get('UICONS_WOFF', '/tmp/uicons-rr.woff'))
cmap, gs = font.getBestCmap(), font.getGlyphSet()
asc, desc = font['hhea'].ascent, font['hhea'].descent
def uicon(name):
    cp = UMAP.get(name); g = cmap.get(cp) if cp else None
    if not g: return None
    pen = SVGPathPen(gs); gs[g].draw(TransformPen(pen, (1, 0, 0, -1, 0, asc)))
    d = pen.getCommands(); return f'<path d="{d}"/>' if d else None
def huge(name):
    i = HUGE.get(name); return i['body'] if i else None
C = {  # key: (hugeicons, uicons)
 'today': (['dashboard-speed-01', 'dashboard-speed-02'], ['dashboard', 'tachometer-fast']),
 'route': (['route-01', 'route-02'], ['route']),
 'trips': (['maps-location-01', 'maps'], ['map-marker', 'map']),
 'logbookIc': (['note-edit', 'notebook'], ['notebook', 'book-alt']),
 'alerts': (['notification-03', 'notification-01'], ['bell-ring', 'bell']),
 'settings': (['settings-04', 'sliders-horizontal', 'settings-02'], ['settings-sliders']),
 'back': (['arrow-left-01'], ['angle-small-left', 'angle-left']),
 'chev': (['arrow-right-01'], ['angle-small-right', 'angle-right']),
 'edit': (['pencil-edit-02', 'pencil-edit-01'], ['pencil']),
 'plus': (['plus-sign'], ['plus']),
 'check': (['tick-02', 'tick-01'], ['check']),
 'x': (['cancel-01'], ['cross-small', 'cross']),
 'fuel': (['fuel-station', 'fuel'], ['gas-pump']),
 'chain': (['link-01', 'link-02'], ['link-alt', 'link']),
 'brake': (['vynil-01', 'cd'], ['record-vinyl', 'disc-drive', 'circle-dashed']),
 'drop': (['droplet'], ['raindrops', 'droplet', 'drop']),
 'gauge': (['dashboard-speed-01'], ['tachometer-fast', 'dashboard']),
 'wind': (['fast-wind', 'wind-power'], ['wind']),
 'wrench': (['wrench-01', 'wrench-02'], ['wrench-simple', 'wrench-alt']),
 'folder': (['folder-01'], ['folder']),
 'sos': (['shield-01', 'security'], ['shield-plus', 'shield-check']),
 'phone': (['call'], ['phone-call']),
 'missed': (['call-missed-01', 'call-missed-02'], ['phone-slash', 'phone-cross']),
 'chat': (['bubble-chat', 'message-01'], ['comment', 'comment-alt']),
 'lock': (['square-lock-02', 'lock'], ['lock']),
 'phoneDev': (['smart-phone-01'], ['mobile-notch', 'smartphone']),
 'bt': (['bluetooth'], ['bluetooth-alt', 'bluetooth']),
 'btSearch': (['bluetooth-search', 'bluetooth'], ['bluetooth-alt', 'bluetooth']),
 'btOn': (['bluetooth-circle', 'bluetooth'], ['bluetooth-alt', 'bluetooth']),
 'play': (['play'], ['play']),
 'stop': (['stop'], ['stop', 'square']),
 'pin': (['location-01'], ['marker']),
 'map': (['maps', 'map-01'], ['map']),
 'down': (['download-01'], ['download']),
 'up': (['upload-01'], ['upload']),
 'copy': (['copy-01'], ['copy-alt', 'copy']),
 'trash': (['delete-02', 'delete-01'], ['trash']),
 'cal': (['calendar-03', 'calendar-01'], ['calendar']),
 'user': (['user'], ['user']),
 'userEdit': (['user-edit-01'], ['user-pen', 'user-gear']),
 'heart': (['heart-check', 'favourite'], ['heart-rate', 'heart']),
 'doc': (['file-01', 'note'], ['document']),
 'palette': (['paint-board'], ['palette']),
 'code': (['source-code'], ['code-simple', 'brackets-curly']),
 'list': (['left-to-right-list-bullet', 'menu-02'], ['list']),
 'hand': (['hand-pointing-right-02', 'hold-01'], ['hand', 'hand-paper']),
 'bolt': (['flash'], ['bolt']),
 'speed': (['dashboard-speed-02', 'dashboard-speed-01'], ['tachometer-fast']),
 'bike': (['motorbike-02', 'motorbike-01'], ['motorcycle']),
 'battery': (['battery-medium-01', 'battery-medium-02'], ['battery-half']),
 'eye': (['view'], ['eye']),
 'garage': (['garage', 'warehouse'], ['garage', 'warehouse-alt']),
 'swap': (['arrow-data-transfer-horizontal', 'arrow-left-right'], ['exchange-alt', 'arrows-h']),
 'image': (['image-01'], ['picture']),
 'camera': (['camera-01'], ['camera']),
 'mail': (['mail-01'], ['envelope']),
 'idcard': (['id', 'user-id-verification'], ['id-badge', 'id-card-clip-alt']),
 'home': (['home-01'], ['home']),
 'nav': (['navigation-03', 'navigation-01'], ['location-arrow']),
 'timer': (['timer-01'], ['stopwatch', 'time-fast']),
 'info': (['information-circle'], ['info']),
 'alert': (['alert-02', 'alert-01'], ['triangle-warning', 'exclamation']),
 'sparkles': (['sparkles'], ['sparkles', 'magic-wand']),
 'ok': (['checkmark-circle-02', 'checkmark-circle-01'], ['check-circle']),
 'parking': (['parking-area-square', 'parking-area-circle'], ['parking-circle', 'parking']),
 'siren': (['police-car', 'alarm-clock'], ['siren-on', 'siren']),
 'hospital': (['hospital-01'], ['hospital']),
 'ambulance': (['ambulance'], ['ambulance']),
 'shield': (['shield-01'], ['shield']),
 'badge': (['checkmark-badge-01'], ['badge-check']),
 'wallet': (['wallet-01'], ['wallet']),
 'rupee': (['rupee', 'rupee-circle'], ['indian-rupee-sign', 'rupee-sign']),
 'flag': (['flag-01'], ['flag-alt', 'flag']),
 'more': (['more-horizontal'], ['menu-dots']),
 'profile': (['user-circle'], ['circle-user']),
 'signal': (['signal-full-02', 'signal'], ['signal-alt-2', 'signal-alt']),
 'bell': (['notification-01'], ['bell']),
 'layers': (['layers-01'], ['layers']),
 'thermo': (['temperature'], ['temperature-high']),
 'clock': (['clock-01'], ['clock']),
 'flask': (['test-tube-01', 'labs'], ['flask']),
 'grid': (['grid-view'], ['apps']),
 'images': (['album-02', 'image-02'], ['images']),
 'volume': (['volume-high'], ['volume']),
 'wxSun': (['sun-03', 'sun-01'], ['sun']),
 'wxPart': (['sun-cloud-02', 'sun-cloud-01'], ['cloud-sun']),
 'wxCloud': (['cloud'], ['cloud']),
 'wxFog': (['cloud-little-rain', 'fog'], ['smog', 'fog']),
 'wxDrizzle': (['cloud-little-rain'], ['cloud-drizzle']),
 'wxRain': (['cloud-big-rain', 'cloud-mid-rain'], ['cloud-showers-heavy', 'cloud-rain']),
 'wxStorm': (['cloud-angled-rain-zap', 'zap'], ['thunderstorm', 'cloud-bolt']),
 'wxSnow': (['snow'], ['snowflake']),
 'wxMoon': (['moon-02'], ['moon']),
 'wxPartNight': (['moon-cloud'], ['cloud-moon']),
 'drops': (['droplet'], ['raindrops', 'droplet']),
 'refresh': (['refresh'], ['refresh']),
}
out = os.environ.get('OUT', 'js/')
for i, (lib, fn, set_, credit) in enumerate([
    ('hugeicons', huge, {'vb': '0 0 24 24', 'attrs': 'fill="none"'}, 'Hugeicons free (MIT), via Iconify'),
    ('uicons', uicon, {'vb': f'0 0 {font["hmtx"]["uni" + "f101"][0] if False else font["head"].unitsPerEm} {asc - desc}', 'attrs': 'fill="currentColor"'}, 'Uicons by Flaticon (Flaticon licence; credit shown in the app)')]):
    got, miss = {}, []
    for k, cands in C.items():
        for n in cands[i]:
            v = fn(n)
            if v: got[k] = v; break
        else: miss.append(k)
    js = f"// Icons: a subset of {credit}. Keys missing here fall back to Lucide.\nexport const SET = {json.dumps(set_)};\nexport const ICONS = {{\n" + ''.join(f"  {json.dumps(k)}: {json.dumps(v)},\n" for k, v in got.items()) + "};\n"
    open(out + f'icons-{lib}.js', 'w').write(js)
    print(lib, len(got), f'{len(js)//1024} KB', 'missing:', miss)
