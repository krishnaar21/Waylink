# Render the 180 px PNG copies of app icon styles (for Home Screen icons) with headless Chrome.
# Usage: python3 tools/icons_png.py leather ascii wool
import asyncio, sys, pathlib
from playwright.async_api import async_playwright
async def main(names):
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox'])
        pg = await b.new_page(viewport={'width': 180, 'height': 180})
        for n in names:
            svg = pathlib.Path(f'img/logos/wl-icon-{n}.svg').read_text()
            await pg.set_content(f'<body style="margin:0;background:transparent">{svg.replace("<svg ", "<svg width=180 height=180 ", 1)}</body>')
            await pg.wait_for_timeout(250)
            await pg.screenshot(path=f'img/logos/png/wl-icon-{n}-180.png', omit_background=True, clip={'x': 0, 'y': 0, 'width': 180, 'height': 180})
            print('png', n)
        await b.close()
asyncio.run(main(sys.argv[1:]))
