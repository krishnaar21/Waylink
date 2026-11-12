# App icon styles: the Waylink w on different backgrounds. Writes img/logos/wl-icon-<style>.svg
D = "M2.5 44C3 64 12 80 25 80C37 80 42 64 46 50C48.8 40.5 54.5 31 54 22C53.6 13.5 39 12.5 38.6 22C38.2 31 43.4 40.5 46.8 50C51 64 58 80 71 80C84 80 90 56 91.5 26L91.5 14"
POLY = "-20,-20 6,-20 6,44 3.8,48.5 2.1,52.5 0.9,56 0.2,59 -0.1,61.5 -20,61.5"
T = 'translate(256 262) scale(3.6) translate(-47 -48)'
def w(stroke, extra='', sw=11, ty=0):
    t = T if not ty else f'translate(256 {262 + ty}) scale(3.6) translate(-47 -48)'
    return (f'<defs><mask id="tp" maskUnits="userSpaceOnUse" x="0" y="0" width="512" height="512"><rect width="512" height="512" fill="#fff"/>'
            f'<g transform="{t}"><polygon points="{POLY}" fill="#000"/></g></mask></defs>'
            f'<g mask="url(#tp)"><g transform="{t}" fill="none" stroke="{stroke}" stroke-width="{sw}" stroke-linejoin="round" {extra}><path d="{D}"/></g></g>')
def svg(body): return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">{body}</svg>'
R = 'rx="114"'
STYLES = {
  'paper': svg(f'<rect width="512" height="512" {R} fill="#F6F1E9"/>' + w('#C2452B')),
  'midnight': svg(f'<defs><radialGradient id="g" cx="50%" cy="38%" r="75%"><stop offset="0" stop-color="#26346B"/><stop offset="1" stop-color="#0B1230"/></radialGradient><filter id="glow" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs><rect width="512" height="512" {R} fill="url(#g)"/>' + w('#E9EEFF', 'filter="url(#glow)"').replace('<defs>', '<defs>', 1)),
  'sunset': svg(f'<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFB547"/><stop offset=".55" stop-color="#F0584A"/><stop offset="1" stop-color="#B8336A"/></linearGradient></defs><rect width="512" height="512" {R} fill="url(#g)"/>' + w('#FFFFFF')),
  'road': svg(f'<rect width="512" height="512" {R} fill="#2A2D33"/>'
              '<path d="M150 512 L236 300 M362 512 L276 300" stroke="#4A4F58" stroke-width="10"/>'
              '<path d="M256 512 L256 300" stroke="#F5C542" stroke-width="10" stroke-dasharray="34 26"/>'
              '<rect width="512" height="512" rx="114" fill="url(#fade)"/><defs><linearGradient id="fade" x1="0" y1="0" x2="0" y2="1"><stop offset=".45" stop-color="#2A2D33"/><stop offset=".62" stop-color="#2A2D33" stop-opacity="0"/></linearGradient></defs>'
              + w('#FFFFFF', ty=-30)),
  'dev': svg(f'<defs><pattern id="grid" width="32" height="32" patternUnits="userSpaceOnUse"><path d="M32 0H0V32" fill="none" stroke="#1B2A1F" stroke-width="2"/></pattern></defs><rect width="512" height="512" {R} fill="#0A0F0B"/><rect width="512" height="512" {R} fill="url(#grid)"/>'
             '<text x="70" y="128" font-family="Menlo, monospace" font-size="54" font-weight="700" fill="#3DDC84" opacity=".55">&gt;_</text>' + w('#3DDC84')),
  'tank': svg(f'<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#E23A2E"/><stop offset="1" stop-color="#A3191A"/></linearGradient><clipPath id="c"><rect width="512" height="512" {R}/></clipPath></defs><rect width="512" height="512" {R} fill="url(#g)"/>'
              '<g clip-path="url(#c)"><path d="M-40 420 L420 -40 L470 -40 L10 420Z" fill="#fff" opacity=".9"/><path d="M30 520 L540 10 L570 40 L60 550Z" fill="#111" opacity=".55"/><path d="M-60 200 C120 160 300 150 600 210" stroke="#fff" stroke-opacity=".18" stroke-width="40" fill="none"/></g>' + w('#FFFFFF', sw=12)),
  'carbon': svg(f'<defs><pattern id="cf" width="24" height="24" patternUnits="userSpaceOnUse"><rect width="24" height="24" fill="#16181C"/><rect width="12" height="12" fill="#22252B"/><rect x="12" y="12" width="12" height="12" fill="#22252B"/></pattern><linearGradient id="sh" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".12"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/></linearGradient></defs><rect width="512" height="512" {R} fill="url(#cf)"/><rect width="512" height="512" {R} fill="url(#sh)"/>' + w('#FF5A3C')),
  'mono': svg(f'<rect width="512" height="512" {R} fill="#111214"/>' + w('#FFFFFF')),
}
if __name__ == '__main__':
    import re
    for k, v in STYLES.items():
        for i in set(re.findall(r'id="([^"]+)"', v)): v = v.replace(f'id="{i}"', f'id="{k}-{i}"').replace(f'url(#{i})', f'url(#{k}-{i})')
        open(f'img/logos/wl-icon-{k}.svg', 'w').write(v)
    print(list(STYLES))
