# App icon styles: the Waylink w as a clean, glossy mark on different backgrounds.
# Writes img/logos/wl-icon-<style>.svg (ids are prefixed per style so icons can sit on one page).
import re
D = "M2.5 44C3 64 12 80 25 80C37 80 42 64 46 50C48.8 40.5 54.5 31 54 22C53.6 13.5 39 12.5 38.6 22C38.2 31 43.4 40.5 46.8 50C51 64 58 80 71 80C84 80 90 56 91.5 26L91.5 14"
POLY = "-20,-20 6,-20 6,44 3.8,48.5 2.1,52.5 0.9,56 0.2,59 -0.1,61.5 -20,61.5"
T = 'translate(256 266) scale(3.5) translate(-47 -48)'
R = 'rx="114"'
def bg(fill, extra=''): return f'<rect width="512" height="512" {R} fill="{fill}"/>{extra}'
def mark(layers, defs=''):
    """layers: list of (stroke, width, extra attrs) drawn bottom to top, all with the tapered start."""
    m = (f'<mask id="tp" maskUnits="userSpaceOnUse" x="0" y="0" width="512" height="512"><rect width="512" height="512" fill="#fff"/>'
         f'<g transform="{T}"><polygon points="{POLY}" fill="#000"/></g></mask>')
    body = ''.join(f'<path d="{D}" fill="none" stroke="{c}" stroke-width="{w}" stroke-linejoin="round" {x}/>' for c, w, x in layers)
    return f'<defs>{m}{defs}</defs><g mask="url(#tp)"><g transform="{T}">{body}</g></g>'
SH = '<filter id="sh" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="2.2" stdDeviation="2.4" flood-color="#000" flood-opacity=".28"/></filter>'
def grad(id, stops, x1=0, y1=0, x2=100, y2=60):
    s = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<linearGradient id="{id}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'
def svg(*parts): return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">' + ''.join(parts) + '</svg>'
gloss = '<rect width="512" height="512" rx="114" fill="url(#gl)"/><defs><linearGradient id="gl" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/></linearGradient></defs>'

def tonal(dark, pale, motif=''):
    return svg(bg(dark), motif, mark([(pale, 11, '')]))
STYLES = {
  # Metallic: brushed steel plate, chrome w with an engraved edge
  'chrome': svg('<defs><linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E7EAEE"/><stop offset=".5" stop-color="#9AA1AB"/><stop offset=".52" stop-color="#B8BEC6"/><stop offset="1" stop-color="#6D737C"/></linearGradient><filter id="br"><feTurbulence type="fractalNoise" baseFrequency=".9 .012" numOctaves="2" seed="4"/><feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .18 0"/><feComposite in2="SourceGraphic" operator="in"/></filter></defs>',
                bg('url(#b)'), '<rect width="512" height="512" rx="114" fill="#fff" filter="url(#br)"/>',
                '<circle cx="74" cy="74" r="11" fill="#5C6169"/><circle cx="438" cy="74" r="11" fill="#5C6169"/><circle cx="74" cy="438" r="11" fill="#5C6169"/><circle cx="438" cy="438" r="11" fill="#5C6169"/>',
                mark([('#2B2F35', 15, 'filter="url(#sh)"'), ('url(#g)', 10, '')], SH + grad('g', [(0, '#FFFFFF'), (.45, '#C9CED6'), (.55, '#7E858F'), (1, '#E9ECF0')], 0, 0, 0, 90))),
  # Retro: 70s tank stripes on cream, chunky w
  'retro': svg(bg('#F3E6C8'), '<defs><clipPath id="c"><rect width="512" height="512" rx="114"/></clipPath></defs><g clip-path="url(#c)">'
               '<rect y="330" width="512" height="34" fill="#E8A13A"/><rect y="372" width="512" height="34" fill="#D9672B"/><rect y="414" width="512" height="34" fill="#A8322A"/></g>',
               mark([('#3A2A1E', 16, ''), ('#F3E6C8', 6, '')])),
  # Future: dark glass, cyan HUD rings, light trail
  # Future (toolkit): deep indigo plate, pale w, a faint HUD ring
  'future': tonal('#1B1F4A', '#B9C2FF', '<circle cx="256" cy="262" r="196" fill="none" stroke="#B9C2FF" stroke-opacity=".16" stroke-width="3" stroke-dasharray="3 9"/><path d="M96 170A196 196 0 0 1 200 74" fill="none" stroke="#B9C2FF" stroke-opacity=".4" stroke-width="5" stroke-linecap="round"/>'),
  'leather': svg('<defs><linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8A4B26"/><stop offset="1" stop-color="#5A2C14"/></linearGradient><filter id="lt"><feTurbulence type="fractalNoise" baseFrequency=".55" numOctaves="3" seed="7"/><feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 .35 0"/><feComposite in2="SourceGraphic" operator="in"/></filter></defs>',
                 bg('url(#b)'), '<rect width="512" height="512" rx="114" fill="#000" filter="url(#lt)"/>',
                 '<rect x="40" y="40" width="432" height="432" rx="88" fill="none" stroke="#E7C79A" stroke-width="4" stroke-dasharray="14 10" opacity=".85"/>',
                 mark([('#2E1609', 14, 'transform="translate(0 1.2)" opacity=".7"'), ('#E7C79A', 10, '')])),
  # Material: flat colour, stacked paper shadows
  # Material (toolkit): deep teal plate, pale w, a soft long shadow
  'material': svg(bg('#0F3B3A'), '<defs><clipPath id="c"><rect width="512" height="512" rx="114"/></clipPath><linearGradient id="sl" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".25"/><stop offset="1" stop-color="#000" stop-opacity="0"/></linearGradient></defs><g clip-path="url(#c)"><path d="M300 100 L620 420 L620 620 L200 620 L120 540Z" fill="url(#sl)"/></g>', mark([('#A8E3D9', 11, '')])),
  # Asphalt (toolkit): charcoal plate, pale w, a dashed lane line under it
  'asphalt': tonal('#26282C', '#E4E1D6', '<path d="M256 452 L256 404" stroke="#E8C35A" stroke-opacity=".7" stroke-width="9" stroke-linecap="round"/><path d="M256 392 L256 366" stroke="#E8C35A" stroke-opacity=".45" stroke-width="7" stroke-linecap="round"/>'),
  'oil': svg('<defs><radialGradient id="b" cx="35%" cy="30%" r="80%"><stop offset="0" stop-color="#22252B"/><stop offset="1" stop-color="#07080A"/></radialGradient><radialGradient id="pool" cx="50%" cy="50%" r="50%"><stop offset="0" stop-color="#3A2A6A" stop-opacity=".55"/><stop offset=".6" stop-color="#14424A" stop-opacity=".3"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient></defs>',
             bg('url(#b)'), '<ellipse cx="256" cy="300" rx="210" ry="150" fill="url(#pool)"/>',
             mark([('#000', 15, 'filter="url(#sh)"'), ('url(#g)', 11, ''), ('#fff', 2.4, 'opacity=".55" transform="translate(-.8 -1.2)"')], SH + grad('g', [(0, '#7B4BFF'), (.3, '#2BD3C4'), (.55, '#E7D44A'), (.8, '#FF4F9A'), (1, '#5B3BFF')]))),
  # Gauge: brass bezel, dial face, needle red w
  # Gauge (toolkit): amber-brown plate, pale w, a ring of dial ticks
  'gauge': tonal('#4A2E0E', '#F2D29B', ''.join(f'<line x1="256" y1="58" x2="256" y2="{74 if k % 3 else 84}" stroke="#F2D29B" stroke-opacity="{.35 if k % 3 else .6}" stroke-width="{3 if k % 3 else 5}" stroke-linecap="round" transform="rotate({-120 + k * 20} 256 262)"/>' for k in range(13))),
}
if __name__ == '__main__':
    import glob, os
    for f in glob.glob('img/logos/wl-icon-*.svg') + glob.glob('img/logos/png/wl-icon-*.png'): os.remove(f)
    for k, v in STYLES.items():
        for i in set(re.findall(r'id="([^"]+)"', v)): v = v.replace(f'id="{i}"', f'id="{k}-{i}"').replace(f'url(#{i})', f'url(#{k}-{i})')
        open(f'img/logos/wl-icon-{k}.svg', 'w').write(v)
    print(list(STYLES))
