import os
def tile(bg, body, defs=''):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs>{defs}</defs><rect width="512" height="512" rx="114" fill="{bg}"/>{body}</svg>'
L={}
# 1 SUMMIT: a sunrise over a W of two peaks, the road curving underneath.
c='#E3F7F3'
rays=''.join(f'<rect x="-17" y="-206" width="34" height="62" rx="3" transform="translate(256 344) rotate({a})" fill="{c}"/>' for a in (-68,-34,0,34,68))
L['wl-summit']=('Summit',tile('#163C42', rays+
  f'<path d="M146 336L201 268L256 322L311 268L366 336" fill="none" stroke="{c}" stroke-width="38" stroke-linejoin="round"/>'
  f'<path d="M118 398Q256 352 394 398" fill="none" stroke="{c}" stroke-width="38"/>'))
# 2 DUSK: sand sky with the sun cut out, two blue roads crossing below like a junction.
sky,blu,bg='#C9B48C','#3C6396','#F4F2EE'
L['wl-dusk']=('Dusk',tile(bg,
  f'<path d="M126 100H386V228C318 228 252 262 126 262Z" fill="{sky}"/><circle cx="184" cy="156" r="28" fill="{bg}"/>'
  '<clipPath id="k"><rect x="126" y="200" width="260" height="230"/></clipPath>'
  f'<g clip-path="url(#k)" fill="none" stroke="{blu}" stroke-width="46">'
  '<path d="M100 314C220 314 292 390 412 390"/><path d="M100 390C220 390 292 314 412 314"/></g>'
  f'<path d="M126 296C230 296 300 270 386 270" fill="none" stroke="{bg}" stroke-width="0"/>'))
# 3 RIBBON: a winding road between two hairpin bends.
L['wl-ribbon']=('Ribbon',tile('#1E1914',
  '<path d="M100 250C206 214 306 306 412 262" fill="none" stroke="#fff" stroke-width="58"/>'
  '<path d="M240 96A120 120 0 0 0 404 196" fill="none" stroke="#E2B979" stroke-width="58"/>'
  '<path d="M272 416A120 120 0 0 0 108 316" fill="none" stroke="#E2B979" stroke-width="58"/>'))
os.makedirs('out',exist_ok=True)
html='<body style="margin:12px;display:flex;gap:16px;background:#ccc">'
for k,(n,s) in L.items():
    open(f'out/{k}.svg','w').write(s); html+=f'<div style="text-align:center;font:14px sans-serif">{s.replace("<svg ","<svg width=160 height=160 ")}<br>{n}</div>'
open('prev.html','w').write(html+'</body>')
