# Map Waylink's icon keys to Tabler, Phosphor and Iconoir. Each entry lists names to try in order.
import os, re, json
LIBS = {
  'tabler':  ('../lib-tabler-icons/icons/outline', '.svg'),
  'phosphor':('../lib-core/assets/regular', '.svg'),
  'iconoir': ('../lib-iconoir/icons/regular', '.svg'),
}
C = {  # key: {lib: [candidates]}  ; generic candidates in '*'
 'today': {'tabler':['gauge'],'phosphor':['gauge'],'iconoir':['dashboard-speed','dashboard']},
 'route': {'tabler':['route'],'phosphor':['path'],'iconoir':['path-arrow','map']},
 'trips': {'tabler':['map-pin-2','map-2'],'phosphor':['map-trifold'],'iconoir':['map-pin','map']},
 'logbookIc': {'tabler':['notebook'],'phosphor':['notebook'],'iconoir':['notes','journal']},
 'alerts': {'tabler':['bell-ringing'],'phosphor':['bell-ringing'],'iconoir':['bell-notification','bell']},
 'settings': {'tabler':['adjustments-horizontal'],'phosphor':['sliders-horizontal'],'iconoir':['settings']},
 'back': {'tabler':['chevron-left'],'phosphor':['caret-left'],'iconoir':['nav-arrow-left']},
 'chev': {'tabler':['chevron-right'],'phosphor':['caret-right'],'iconoir':['nav-arrow-right']},
 'edit': {'tabler':['pencil'],'phosphor':['pencil-simple'],'iconoir':['edit-pencil']},
 'plus': {'tabler':['plus'],'phosphor':['plus'],'iconoir':['plus']},
 'check': {'tabler':['check'],'phosphor':['check'],'iconoir':['check']},
 'x': {'tabler':['x'],'phosphor':['x'],'iconoir':['xmark']},
 'fuel': {'tabler':['gas-station'],'phosphor':['gas-pump'],'iconoir':['gas-tank-droplet','gas']},
 'chain': {'tabler':['link'],'phosphor':['link'],'iconoir':['link']},
 'brake': {'tabler':['disc'],'phosphor':['disc'],'iconoir':['disc','circle']},
 'drop': {'tabler':['droplet'],'phosphor':['drop'],'iconoir':['droplet']},
 'gauge': {'tabler':['gauge'],'phosphor':['gauge'],'iconoir':['dashboard-speed','dashboard']},
 'wind': {'tabler':['wind'],'phosphor':['wind'],'iconoir':['wind']},
 'wrench': {'tabler':['tool'],'phosphor':['wrench'],'iconoir':['tools','wrench']},
 'folder': {'tabler':['folder'],'phosphor':['folder'],'iconoir':['folder']},
 'sos': {'tabler':['shield-plus'],'phosphor':['first-aid-kit'],'iconoir':['shield-check','first-aid-kit','healthcare']},
 'phone': {'tabler':['phone'],'phosphor':['phone'],'iconoir':['phone']},
 'missed': {'tabler':['phone-x'],'phosphor':['phone-x'],'iconoir':['phone-xmark','phone-disabled']},
 'chat': {'tabler':['message-circle'],'phosphor':['chat-circle'],'iconoir':['chat-bubble']},
 'lock': {'tabler':['lock'],'phosphor':['lock'],'iconoir':['lock']},
 'phoneDev': {'tabler':['device-mobile'],'phosphor':['device-mobile'],'iconoir':['smartphone-device']},
 'bt': {'tabler':['bluetooth'],'phosphor':['bluetooth'],'iconoir':['bluetooth']},
 'btSearch': {'tabler':['bluetooth'],'phosphor':['bluetooth'],'iconoir':['bluetooth']},
 'btOn': {'tabler':['bluetooth-connected'],'phosphor':['bluetooth-connected'],'iconoir':['bluetooth']},
 'play': {'tabler':['player-play'],'phosphor':['play'],'iconoir':['play']},
 'stop': {'tabler':['player-stop'],'phosphor':['stop'],'iconoir':['square']},
 'pin': {'tabler':['map-pin'],'phosphor':['map-pin'],'iconoir':['map-pin']},
 'map': {'tabler':['map'],'phosphor':['map-trifold'],'iconoir':['map']},
 'down': {'tabler':['download'],'phosphor':['download-simple'],'iconoir':['download']},
 'up': {'tabler':['upload'],'phosphor':['upload-simple'],'iconoir':['upload']},
 'copy': {'tabler':['copy'],'phosphor':['copy'],'iconoir':['copy']},
 'trash': {'tabler':['trash'],'phosphor':['trash'],'iconoir':['trash']},
 'cal': {'tabler':['calendar'],'phosphor':['calendar-blank'],'iconoir':['calendar']},
 'user': {'tabler':['user'],'phosphor':['user'],'iconoir':['user']},
 'userEdit': {'tabler':['user-edit'],'phosphor':['user-gear','user'],'iconoir':['edit-user','user']},
 'heart': {'tabler':['heartbeat'],'phosphor':['heartbeat'],'iconoir':['heart']},
 'doc': {'tabler':['file-text'],'phosphor':['file-text'],'iconoir':['page']},
 'palette': {'tabler':['palette'],'phosphor':['palette'],'iconoir':['color-filter','palette']},
 'code': {'tabler':['code'],'phosphor':['code'],'iconoir':['code']},
 'list': {'tabler':['list'],'phosphor':['list'],'iconoir':['list']},
 'hand': {'tabler':['hand-stop'],'phosphor':['hand'],'iconoir':['hand-brake','hand-card','hand']},
 'bolt': {'tabler':['bolt'],'phosphor':['lightning'],'iconoir':['flash']},
 'speed': {'tabler':['gauge'],'phosphor':['speedometer','gauge'],'iconoir':['dashboard-speed']},
 'bike': {'tabler':['motorbike'],'phosphor':['motorcycle'],'iconoir':['motorcycle','bicycle']},
 'battery': {'tabler':['battery-2'],'phosphor':['battery-medium'],'iconoir':['battery-50']},
 'eye': {'tabler':['eye'],'phosphor':['eye'],'iconoir':['eye']},
 'garage': {'tabler':['building-warehouse'],'phosphor':['warehouse','garage'],'iconoir':['home-alt','home']},
 'swap': {'tabler':['arrows-left-right'],'phosphor':['arrows-left-right'],'iconoir':['data-transfer-both','arrow-separate']},
 'image': {'tabler':['photo'],'phosphor':['image'],'iconoir':['media-image']},
 'camera': {'tabler':['camera'],'phosphor':['camera'],'iconoir':['camera']},
 'mail': {'tabler':['mail'],'phosphor':['envelope-simple'],'iconoir':['mail']},
 'idcard': {'tabler':['id'],'phosphor':['identification-card'],'iconoir':['id-card','credit-card']},
 'home': {'tabler':['home'],'phosphor':['house'],'iconoir':['home']},
 'nav': {'tabler':['navigation'],'phosphor':['navigation-arrow'],'iconoir':['navigator-alt','navigator']},
 'timer': {'tabler':['clock-hour-3','alarm'],'phosphor':['timer'],'iconoir':['timer']},
 'info': {'tabler':['info-circle'],'phosphor':['info'],'iconoir':['info-circle']},
 'alert': {'tabler':['alert-triangle'],'phosphor':['warning'],'iconoir':['warning-triangle']},
 'sparkles': {'tabler':['sparkles'],'phosphor':['sparkle'],'iconoir':['sparks']},
 'ok': {'tabler':['circle-check'],'phosphor':['check-circle'],'iconoir':['check-circle']},
 'parking': {'tabler':['parking'],'phosphor':['letter-circle-p','local-parking'],'iconoir':['parking']},
 'siren': {'tabler':['urgent'],'phosphor':['siren'],'iconoir':['bell-notification','warning-circle']},
 'hospital': {'tabler':['building-hospital'],'phosphor':['hospital'],'iconoir':['hospital']},
 'ambulance': {'tabler':['ambulance'],'phosphor':['ambulance'],'iconoir':['truck','delivery-truck']},
 'shield': {'tabler':['shield'],'phosphor':['shield'],'iconoir':['shield']},
 'badge': {'tabler':['rosette-discount-check','discount-check'],'phosphor':['seal-check'],'iconoir':['badge-check','check-circle']},
 'wallet': {'tabler':['wallet'],'phosphor':['wallet'],'iconoir':['wallet']},
 'rupee': {'tabler':['currency-rupee'],'phosphor':['currency-inr'],'iconoir':['coins','money-square']},
 'flag': {'tabler':['flag'],'phosphor':['flag'],'iconoir':['triangle-flag','flag']},
 'more': {'tabler':['dots'],'phosphor':['dots-three'],'iconoir':['more-horiz']},
 'profile': {'tabler':['user-circle'],'phosphor':['user-circle'],'iconoir':['profile-circle']},
 'signal': {'tabler':['antenna-bars-3','chart-bar'],'phosphor':['cell-signal-medium'],'iconoir':['antenna-signal']},
 'bell': {'tabler':['bell'],'phosphor':['bell'],'iconoir':['bell']},
 'layers': {'tabler':['stack-2'],'phosphor':['stack'],'iconoir':['multiple-pages-empty','copy']},
 'thermo': {'tabler':['temperature'],'phosphor':['thermometer'],'iconoir':['temperature-high','thermometer']},
 'clock': {'tabler':['clock'],'phosphor':['clock'],'iconoir':['clock']},
 'flask': {'tabler':['flask'],'phosphor':['flask'],'iconoir':['flask']},
 'grid': {'tabler':['layout-grid'],'phosphor':['squares-four'],'iconoir':['view-grid']},
 'images': {'tabler':['photo-scan','library-photo'],'phosphor':['images'],'iconoir':['media-image-list']},
 'volume': {'tabler':['volume'],'phosphor':['speaker-high'],'iconoir':['sound-high']},
 'wxSun': {'tabler':['sun'],'phosphor':['sun'],'iconoir':['sun-light']},
 'wxPart': {'tabler':['cloud-sun','sun-high'],'phosphor':['cloud-sun'],'iconoir':['cloud-sunny']},
 'wxCloud': {'tabler':['cloud'],'phosphor':['cloud'],'iconoir':['cloud']},
 'wxFog': {'tabler':['cloud-fog'],'phosphor':['cloud-fog'],'iconoir':['fog']},
 'wxDrizzle': {'tabler':['cloud-rain'],'phosphor':['cloud-rain'],'iconoir':['rain']},
 'wxRain': {'tabler':['cloud-storm','cloud-rain'],'phosphor':['cloud-rain'],'iconoir':['heavy-rain','rain']},
 'wxStorm': {'tabler':['cloud-bolt','cloud-storm'],'phosphor':['cloud-lightning'],'iconoir':['thunderstorm']},
 'wxSnow': {'tabler':['snowflake'],'phosphor':['snowflake'],'iconoir':['snow-flake','snow']},
 'wxMoon': {'tabler':['moon'],'phosphor':['moon'],'iconoir':['half-moon']},
 'wxPartNight': {'tabler':['moon-stars','cloud'],'phosphor':['cloud-moon'],'iconoir':['cloud-moon','half-moon']},
 'drops': {'tabler':['droplets'],'phosphor':['drop-half','drop'],'iconoir':['droplet']},
 'refresh': {'tabler':['refresh'],'phosphor':['arrows-clockwise'],'iconoir':['refresh']},
}
def inner(svg, lib):
    svg = re.sub(r'<!--[\s\S]*?-->', '', svg)
    s = re.sub(r'^[\s\S]*?<svg[^>]*>', '', svg); s = re.sub(r'</svg>[\s\S]*$', '', s)
    s = re.sub(r'<path stroke="none" d="M0 0h24v24H0z" fill="none"\s*/>', '', s)
    s = re.sub(r'\s*\n\s*', '', s).replace(' />', '/>').strip()
    if lib == 'iconoir': s = s.replace(' stroke="currentColor"', '').replace(' stroke-linecap="round"', '').replace(' stroke-linejoin="round"', '')
    return s
WRAP = {
  'tabler':   {'vb': '0 0 24 24', 'attrs': 'fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"', 'credit': 'Tabler Icons (MIT)'},
  'phosphor': {'vb': '0 0 256 256', 'attrs': 'fill="currentColor"', 'credit': 'Phosphor Icons (MIT)'},
  'iconoir':  {'vb': '0 0 24 24', 'attrs': 'fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"', 'credit': 'Iconoir (MIT)'},
}
out = 'js/'  # run from the repo root, with tabler-icons, phosphor core and iconoir cloned beside it
for lib, (d, ext) in LIBS.items():
    got, miss = {}, []
    for k, cands in C.items():
        for n in cands.get(lib, []):
            p = os.path.join(d, n + ext)
            if os.path.exists(p): got[k] = inner(open(p).read(), lib); break
        else: miss.append(k)
    w = WRAP[lib]
    js = f"// Icons: a subset of {w['credit']}. Keys missing here fall back to Lucide.\nexport const SET = {json.dumps({'vb': w['vb'], 'attrs': w['attrs']})};\nexport const ICONS = {{\n" + ''.join(f"  {json.dumps(k)}: {json.dumps(v)},\n" for k, v in got.items()) + "};\n"
    open(out + f'icons-{lib}.js', 'w').write(js)
    print(lib, len(got), 'icons', len(js)//1024, 'KB; missing:', miss)
