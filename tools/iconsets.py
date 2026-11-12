# Waylink icon keys -> Material Symbols (Rounded), Fluent UI (Regular 24), MingCute (Regular). Missing keys fall back to Lucide.
import os, re, json, subprocess, glob
# Clone google/material-design-icons, microsoft/fluentui-system-icons and Richard9394/MingCute beside this repo.
M = '../lib-material'; F = '../lib-fluentui-system-icons'; MC = '../lib-MingCute/assets/svg/core/regular'
mc_index = {os.path.basename(p)[:-4]: p for p in glob.glob(MC + '/*/*.svg')}
def git_show(repo, path):
    r = subprocess.run(['git', '-C', repo, 'show', f'HEAD:{path}'], capture_output=True, text=True)
    return r.stdout if r.returncode == 0 and '<svg' in r.stdout else None
def get(lib, name):
    if lib == 'material': return git_show(M, f'symbols/web/{name}/materialsymbolsrounded/{name}_24px.svg')
    if lib == 'fluent':
        folder = ' '.join(w.capitalize() for w in name.split('_'))
        return git_show(F, f'assets/{folder}/SVG/ic_fluent_{name}_24_regular.svg')
    if lib == 'mingcute':
        p = mc_index.get(name); return open(p).read() if p else None
# key: (material, fluent, mingcute) candidate lists
C = {
 'today': (['speed'], ['gauge'], ['dashboard_3', 'dashboard_2']),
 'route': (['route'], ['road'], ['route']),
 'trips': (['map'], ['map'], ['map_pin', 'map']),
 'logbookIc': (['menu_book', 'book_2'], ['notebook'], ['notebook', 'book_2']),
 'alerts': (['notifications_active'], ['alert_on'], ['notification', 'bell_ringing']),
 'settings': (['tune'], ['options'], ['settings_6', 'settings_1']),
 'back': (['chevron_left'], ['chevron_left'], ['left']),
 'chev': (['chevron_right'], ['chevron_right'], ['right']),
 'edit': (['edit'], ['edit'], ['pencil', 'edit_2']),
 'plus': (['add'], ['add'], ['add']),
 'check': (['check'], ['checkmark'], ['check']),
 'x': (['close'], ['dismiss'], ['close']),
 'fuel': (['local_gas_station'], ['gas_pump'], ['gas_station']),
 'chain': (['link'], ['link'], ['link']),
 'brake': (['album', 'radio_button_checked'], ['record'], ['disc', 'record_mail', 'target']),
 'drop': (['water_drop'], ['drop'], ['drop']),
 'gauge': (['speed'], ['gauge'], ['dashboard_3']),
 'wind': (['air'], ['weather_blowing_snow', 'top_speed'], ['wind']),
 'wrench': (['build'], ['wrench'], ['tool', 'wrench']),
 'folder': (['folder'], ['folder'], ['folder']),
 'sos': (['health_and_safety'], ['shield_add', 'shield_checkmark'], ['shield_shape', 'safe_shield_2']),
 'phone': (['call'], ['call'], ['phone']),
 'missed': (['phone_missed'], ['call_missed'], ['phone_block', 'phone_off']),
 'chat': (['chat_bubble'], ['chat'], ['chat_1']),
 'lock': (['lock'], ['lock_closed'], ['lock']),
 'phoneDev': (['smartphone'], ['phone'], ['cellphone']),
 'bt': (['bluetooth'], ['bluetooth'], ['bluetooth']),
 'btSearch': (['bluetooth_searching'], ['bluetooth_searching'], ['bluetooth']),
 'btOn': (['bluetooth_connected'], ['bluetooth_connected'], ['bluetooth']),
 'play': (['play_arrow'], ['play'], ['play']),
 'stop': (['stop'], ['stop'], ['stop']),
 'pin': (['location_on'], ['location'], ['location']),
 'map': (['map'], ['map'], ['map']),
 'down': (['download'], ['arrow_download'], ['download_2']),
 'up': (['upload'], ['arrow_upload'], ['upload_2']),
 'copy': (['content_copy'], ['copy'], ['copy_2']),
 'trash': (['delete'], ['delete'], ['delete_2']),
 'cal': (['calendar_today'], ['calendar'], ['calendar']),
 'user': (['person'], ['person'], ['user_3']),
 'userEdit': (['manage_accounts'], ['person_edit'], ['user_edit']),
 'heart': (['ecg_heart', 'monitor_heart'], ['heart_pulse'], ['heart_crack', 'heart']),
 'doc': (['description'], ['document_text'], ['file']),
 'palette': (['palette'], ['color'], ['palette']),
 'code': (['code'], ['code'], ['code']),
 'list': (['list'], ['list', 'text_bullet_list_ltr'], ['list_check']),
 'hand': (['back_hand'], ['hand_right'], ['hand']),
 'bolt': (['bolt'], ['flash'], ['flash']),
 'speed': (['speed'], ['top_speed', 'gauge'], ['dashboard_3']),
 'bike': (['two_wheeler', 'motorcycle'], ['vehicle_motorcycle'], ['bike', 'scooter']),
 'battery': (['battery_horiz_050', 'battery_4_bar'], ['battery_5'], ['battery_2']),
 'eye': (['visibility'], ['eye'], ['eye_2']),
 'garage': (['garage_home', 'garage'], ['building_retail'], ['garage', 'home_3']),
 'swap': (['swap_horiz'], ['arrow_swap'], ['transfer_horizontal', 'arrows_left_right']),
 'image': (['image'], ['image'], ['pic']),
 'camera': (['photo_camera'], ['camera'], ['camera_2']),
 'mail': (['mail'], ['mail'], ['mail']),
 'idcard': (['id_card', 'badge'], ['contact_card'], ['idcard', 'vcard', 'contacts', 'bank_card']),
 'home': (['home'], ['home'], ['home_4']),
 'nav': (['navigation'], ['navigation'], ['navigation']),
 'timer': (['timer'], ['timer'], ['time']),
 'info': (['info'], ['info'], ['information']),
 'alert': (['warning'], ['warning'], ['warning']),
 'sparkles': (['auto_awesome'], ['sparkle'], ['sparkles']),
 'ok': (['check_circle'], ['checkmark_circle'], ['check_circle']),
 'parking': (['local_parking'], ['vehicle_car_parking'], ['parking']),
 'siren': (['e911_emergency', 'notification_important'], ['alert_urgent'], ['alarm_2']),
 'hospital': (['local_hospital'], ['doctor', 'building_hospital'], ['hospital']),
 'ambulance': (['ambulance', 'emergency'], ['vehicle_truck_profile'], ['truck']),
 'shield': (['shield'], ['shield'], ['shield']),
 'badge': (['verified'], ['ribbon'], ['certificate']),
 'wallet': (['account_balance_wallet', 'wallet'], ['wallet'], ['wallet']),
 'rupee': (['currency_rupee'], ['currency_dollar_rupee'], ['currency_rupee']),
 'flag': (['flag'], ['flag'], ['flag_1']),
 'more': (['more_horiz'], ['more_horizontal'], ['more_1']),
 'profile': (['account_circle'], ['person_circle'], ['user_4', 'user_3']),
 'signal': (['signal_cellular_alt'], ['cellular_data_1'], ['signal']),
 'bell': (['notifications'], ['alert'], ['notification']),
 'layers': (['layers'], ['layer'], ['layers']),
 'thermo': (['thermostat'], ['temperature'], ['thermometer']),
 'clock': (['schedule'], ['clock'], ['time']),
 'flask': (['science'], ['beaker'], ['flask']),
 'grid': (['grid_view'], ['grid'], ['grid']),
 'images': (['photo_library'], ['image_multiple'], ['pic_2', 'pic']),
 'volume': (['volume_up'], ['speaker_2'], ['volume']),
 'wxSun': (['clear_day', 'wb_sunny'], ['weather_sunny'], ['sun']),
 'wxPart': (['partly_cloudy_day'], ['weather_partly_cloudy_day'], ['sun_cloudy', 'cloudy']),
 'wxCloud': (['cloud'], ['weather_cloudy'], ['cloud']),
 'wxFog': (['foggy'], ['weather_fog'], ['fog']),
 'wxDrizzle': (['rainy_light', 'rainy'], ['weather_drizzle'], ['drizzle', 'light_rain']),
 'wxRain': (['rainy'], ['weather_rain'], ['rain', 'heavy_rain']),
 'wxStorm': (['thunderstorm'], ['weather_thunderstorm'], ['thunderstorm']),
 'wxSnow': (['weather_snowy', 'ac_unit'], ['weather_snow'], ['snowflake']),
 'wxMoon': (['bedtime', 'dark_mode'], ['weather_moon'], ['moon']),
 'wxPartNight': (['partly_cloudy_night'], ['weather_partly_cloudy_night'], ['moon_cloudy', 'moon']),
 'drops': (['water_drop'], ['drop'], ['drop']),
 'refresh': (['refresh'], ['arrow_clockwise'], ['refresh_2', 'refresh_1']),
}
VB = {'material': '0 -960 960 960', 'fluent': '0 0 24 24', 'mingcute': '0 0 24 24'}
CREDIT = {'material': 'Material Symbols (Apache 2.0)', 'fluent': 'Fluent UI System Icons (MIT)', 'mingcute': 'MingCute (Apache 2.0)'}
def inner(svg):
    s = re.sub(r'^[\s\S]*?<svg[^>]*>', '', svg); s = re.sub(r'</svg>[\s\S]*$', '', s)
    s = re.sub(r'\s*\n\s*', '', s).replace(' />', '/>').strip()
    s = re.sub(r' fill="(#[0-9A-Fa-f]{3,6}|black|currentColor)"', '', s)  # take colour from currentColor
    return s
out = os.environ.get('OUT', 'js/')
for i, lib in enumerate(['material', 'fluent', 'mingcute']):
    if lib != 'material': continue  # only Material Symbols ships now
    got, miss = {}, []
    for k, cands in C.items():
        for n in cands[i]:
            svg = get(lib, n)
            if svg: got[k] = inner(svg); break
        else: miss.append(k)
    js = f"// Icons: a subset of {CREDIT[lib]}. Keys missing here fall back to Lucide.\nexport const SET = {json.dumps({'vb': VB[lib], 'attrs': 'fill=\"currentColor\"'})};\nexport const ICONS = {{\n" + ''.join(f"  {json.dumps(k)}: {json.dumps(v)},\n" for k, v in got.items()) + "};\n"
    open(out + f'icons-{lib}.js', 'w').write(js)
    print(lib, len(got), f'{len(js)//1024} KB', 'missing:', miss)
