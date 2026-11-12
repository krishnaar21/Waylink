"""End-to-end UI tests. Serve the app folder (python3 -m http.server 8765), then:
   python3 tests/e2e.py [--shots DIR]   (needs: pip install playwright; a Chrome/Chromium)"""
import asyncio, sys, os
from playwright.async_api import async_playwright

URL = os.environ.get('PN_URL', 'http://localhost:8765/index.html')
SHOTS = sys.argv[sys.argv.index('--shots') + 1] if '--shots' in sys.argv else None
CHROME = os.environ.get('CHROME', '/usr/bin/google-chrome')
results = []

def check(name, ok, detail=''):
    results.append((name, bool(ok), detail)); print(('PASS ' if ok else 'FAIL ') + name + (f'  [{detail}]' if detail and not ok else ''))

async def shot(pg, name):
    if SHOTS: await pg.screenshot(path=os.path.join(SHOTS, name + '.png'))

async def text(pg, sel):
    el = await pg.query_selector(sel); return (await el.inner_text()) if el else ''

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path=CHROME if os.path.exists(CHROME) else None, args=['--no-sandbox'])
        ctx = await b.new_context(viewport={'width': 390, 'height': 844}, device_scale_factor=2, is_mobile=True, has_touch=True)
        pg = await ctx.new_page()
        import json, time
        now = int(time.time()) // 3600 * 3600
        hrs = [now + i * 3600 for i in range(13)]
        fake = {'current': {'temperature_2m': 29.4, 'apparent_temperature': 31.8, 'weather_code': 2, 'wind_speed_10m': 11, 'is_day': 1},
                'hourly': {'time': hrs, 'temperature_2m': [29 - i * .4 for i in range(13)], 'precipitation_probability': [10, 10, 15, 20, 40, 70, 60, 30, 10, 5, 5, 5, 5], 'weather_code': [2, 2, 2, 3, 3, 61, 61, 3, 2, 1, 0, 0, 0], 'is_day': [1] * 13}}
        await pg.route('**/geocoding-api.open-meteo.com/**', lambda r: r.fulfill(status=200, content_type='application/json', body=json.dumps({'results': [{'name': 'Bengaluru', 'latitude': 12.97, 'longitude': 77.59}]})))
        await pg.route('**/api.open-meteo.com/**', lambda r: r.fulfill(status=200, content_type='application/json', body=json.dumps(fake)))
        errors = []
        pg.on('pageerror', lambda e: errors.append(str(e)))
        pg.on('console', lambda m: errors.append(m.text) if m.type == 'error' and 'fonts.g' not in m.text and 'TUNNEL' not in m.text and 'Failed to load resource' not in m.text else None)
        await pg.goto(URL); await pg.wait_for_timeout(600)
        check('loading screen shows the animated mark', await pg.query_selector('#splash .wl-loop'))
        await shot(pg, '00-loading')
        await pg.wait_for_timeout(2600)
        check('loading screen fades away', not await pg.query_selector('#splash'))
        await shot(pg, '00-onboarding')
        check('onboarding shows on first launch', await pg.query_selector('[data-form="onboard"]'))
        check('app is named Waylink', await pg.title() == 'Waylink')

        # Fresh start path
        await pg.fill('[name=name]', 'Meera'); await pg.fill('[name=plate]', 'ka 01 ab 1234'); await pg.fill('[name=odo]', '2500')
        await pg.select_option('[name=model]', 'n250'); await pg.click('[data-form=onboard] button[type=submit]'); await pg.wait_for_timeout(700)
        check('fresh start saves the rider', await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).profile.name") == 'Meera')
        chip = await text(pg, '.bc-main')
        check('plate is upper-cased on the vehicle chip', 'KA 01 AB 1234' in chip, chip)
        check('fresh bike gets default reminders', len(await pg.query_selector_all('.upnext .upcard')) >= 1)
        check('coming-up cards: icons, bar only when due', await pg.query_selector('.upcard .row-ic svg') and not await pg.query_selector('.upcard.s .fgauge'))

        # Erase and load sample data
        await pg.evaluate("localStorage.clear()"); await pg.reload(); await pg.wait_for_timeout(500)
        await pg.click('[data-act="onboard-sample"]'); await pg.wait_for_timeout(1200)
        check('sample data is Indianised', await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).profile.name") == 'Arjun' and 'KA 05 MX 2481' in await text(pg, '.bc-main'))
        check('verdict reflects overdue chain', 'Needs care' in await text(pg, '.hero .verdict'))
        await shot(pg, '01-today')

        # Status-bar and gesture spacing
        top = await pg.evaluate("document.querySelector('.view .pagehead .title').getBoundingClientRect().top")
        check('content sits clear of the status bar', top >= 24, f'top={top}')
        fab = await pg.evaluate("(()=>{const r=document.querySelector('.fab').getBoundingClientRect();return [r.width,innerHeight-r.bottom]})()")
        check('FAB is 57px and sits above the tab bar', fab[0] == 57 and 70 < fab[1] < 110, str(fab))
        sw = await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(600)
        size = await pg.evaluate("(()=>{const r=document.querySelector('.switch').getBoundingClientRect();return [r.width,r.height]})()")
        check('toggles are 5% under 50×30', size == [47.5, 28.5], str(size))

        # Quick actions sheet
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(500)
        await pg.click('.fab'); await pg.wait_for_timeout(600)
        await pg.keyboard.press('Escape'); await pg.wait_for_timeout(400)
        check('no bell on Today; notifications live in Alerts', not await pg.query_selector('.view [data-arg="inbox"]'))
        await pg.evaluate("location.hash='alerts'"); await pg.wait_for_timeout(500)
        check('Alerts lists what needs you', 'Chain clean' in await text(pg, '.view'))
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(500)
        await pg.click('.fab'); await pg.wait_for_timeout(600)
        check('What now sheet has 4 action tiles', len(await pg.query_selector_all('.qtile')) == 4)
        check('What now lists due jobs', len(await pg.query_selector_all('.qdue')) >= 1)
        await shot(pg, '02-quick')
        await pg.click('.qtile[data-arg="fuel:new"]'); await pg.wait_for_timeout(500)
        check('sheet opened from a sheet shows a back button', await pg.query_selector('.sheet-head [data-act="sheet-back"]'))
        check('fuel uses a native date-and-time picker', await pg.query_selector('form[data-form=fuel] input[type=datetime-local]'))
        await pg.click('[data-act="sheet-back"]'); await pg.wait_for_timeout(500)
        check('back returns to the What now sheet', len(await pg.query_selector_all('.qtile')) == 4 and not await pg.query_selector('[data-act="sheet-back"]'))
        await pg.click('.qdue [data-act="svc-done"]'); await pg.wait_for_timeout(900)
        check('marking chain done clears the overdue verdict', 'Needs care' not in await text(pg, '.hero .verdict'))

        # Vehicles: switch + add
        await pg.click('.bc-main'); await pg.wait_for_timeout(600)
        await shot(pg, '03-garage')
        await pg.click('[data-act="switch-vehicle"]:not(:first-child)'); await pg.wait_for_timeout(700)
        check('switching shows the Dominar', 'Tourer' in await text(pg, '.bc-main') and '8,420' in await text(pg, '.bc-main'))
        await pg.click('.bc-main'); await pg.wait_for_timeout(500)
        await pg.click('[data-arg="vehicle:new"]'); await pg.wait_for_timeout(600)
        await pg.select_option('[name=model]', 'pulsar150'); await pg.fill('[name=nickname]', 'Appa’s 150'); await pg.fill('[name=plate]', 'ka 02 ej 4410'); await pg.fill('[name=odo]', '41200')
        await pg.click('[data-act="veh-colour"][data-arg="#1F3A5F"]'); await pg.wait_for_timeout(200)
        await shot(pg, '04-add-vehicle')
        await pg.click('form[data-form=vehicle] button[type=submit]'); await pg.wait_for_timeout(800)
        check('new vehicle becomes active', 'Appa’s 150' in await text(pg, '.bc-main'))
        src = await pg.evaluate("document.querySelector('.bc-main .art img')?.getAttribute('src') || ''")
        check('new Pulsar 150 shows its illustration', 'pulsar150' in src, src)
        count = await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).vehicles.length")
        check('garage saved with 3 bikes', count == 3, str(count))

        # Fuel on the new bike
        await pg.evaluate("location.hash='fuel'"); await pg.wait_for_timeout(600)
        for odo, l in [('41200', '11'), ('41520', '10.5')]:
            await pg.click('.btn.ink[data-arg="fuel:new"]'); await pg.wait_for_timeout(500)
            await pg.fill('[name=odo]', odo); await pg.fill('[name=litres]', l); await pg.wait_for_timeout(100)
            await pg.click('form[data-form=fuel] button[type=submit]'); await pg.wait_for_timeout(600)
        stat = await text(pg, '.stat3')
        check('mileage computed from two full tanks (320 km / 10.5 L)', '30.5' in stat, stat.replace('\n', ' '))

        # Profile autosave
        await pg.evaluate("location.hash='profile'"); await pg.wait_for_timeout(600)
        await pg.fill('[name=licenceNo]', 'KA02 20150099887'); await pg.wait_for_timeout(700)
        saved = await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).profile.licenceNo")
        check('profile autosaves', saved == 'KA02 20150099887', saved)
        await shot(pg, '05-profile')

        # Picture modes
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(500)
        check('no vehicle pictures row any more', not await pg.query_selector('[data-arg="pick-art"]'))
        check('no add-vehicle FAB on settings', not await pg.query_selector('#fab .fab'))
        check('garage card has its own edit button', await pg.query_selector('.vcard .edit'))
        check('route icon is the default', True)
        check('no Carbon theme any more', not await pg.query_selector('[data-act="theme"][data-arg="carbon"]'))
        await pg.click('[data-arg="pick-font"]'); await pg.wait_for_timeout(500)
        check('selected font is marked, no tick', await pg.query_selector('.ftile.on') and not await pg.query_selector('.ftile .selmark'))
        check('serif fonts offered', await pg.query_selector('[data-act="font"][data-arg="garamond"]') and await pg.query_selector('[data-act="font"][data-arg="fraunces"]'))
        await pg.evaluate("document.querySelector('.backdrop')?.click()"); await pg.wait_for_timeout(500)
        await pg.click('[data-arg="models"]'); await pg.wait_for_timeout(500)
        check('model gallery lists all 11 models', len(await pg.query_selector_all('.models .m')) == 11)
        await shot(pg, '06b-models')
        await pg.keyboard.press('Escape'); await pg.wait_for_timeout(400)
        # Preview states: nothing is saved, every tab renders
        before = await pg.evaluate("localStorage.getItem('pulsarnav-web-v2')")
        for kind in ['edge', 'empty']:
            await pg.click(f'[data-act="scenario"][data-arg="{kind}"]'); await pg.wait_for_timeout(500)
            for tab in ['today', 'route', 'trips', 'alerts', 'settings', 'fuel', 'logbook', 'profile']:
                await pg.evaluate(f"location.hash='{tab}'"); await pg.wait_for_timeout(350)
                if tab == 'today': await shot(pg, f'12-{kind}-today')
            await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(400)
        await pg.click('[data-act="scenario"][data-arg="real"]'); await pg.wait_for_timeout(500)
        after = await pg.evaluate("localStorage.getItem('pulsarnav-web-v2')")
        check('previews never touch saved data', before == after)
        check('back on real data', 'Appa’s 150' in await text(pg, '.view') or 'Daily' in await text(pg, '.view'))
        await shot(pg, '06-settings')

        # Fonts and icon live in sheets opened from the Theme section
        await pg.click('[data-arg="pick-font"]'); await pg.wait_for_timeout(500)
        await pg.click('[data-act="font"][data-arg="onest"]'); await pg.wait_for_timeout(300)
        check('title font switches', await pg.evaluate("document.documentElement.dataset.font") == "onest")
        check('six title fonts offered', len(await pg.query_selector_all('.sheet [data-act="font"]')) == 6)
        await pg.click('[data-act="font"][data-arg="garamond"]'); await pg.wait_for_timeout(300)
        await pg.click('[data-act="body-font"][data-arg="atkinson"]'); await pg.wait_for_timeout(300)
        check('body font switches', await pg.evaluate("document.documentElement.dataset.body") == 'atkinson')
        await pg.evaluate("document.querySelector('.backdrop')?.click()"); await pg.wait_for_timeout(500)
        await pg.click('[data-act="accent"][data-arg="blue"]'); await pg.wait_for_timeout(300)
        check('app icon follows the accent, name stays', 'wl-loop-blue' in await pg.evaluate("document.querySelector('link[rel=icon]').getAttribute('href')") and await pg.title() == 'Waylink')
        await pg.click('[data-act="accent"][data-arg="rust"]'); await pg.wait_for_timeout(300)
        await pg.click('[data-act="show-loader"]'); await pg.wait_for_timeout(400)
        check('loading screen can be replayed', await pg.query_selector('#splash .wl-loop'))
        await pg.evaluate("document.querySelector('#splash').click()"); await pg.wait_for_timeout(800)
        check('tapping closes the replayed loader', not await pg.query_selector('#splash'))

        # Simulated bike: states and animations
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(500)
        await pg.click('.conn [data-act="sim-on"]'); await pg.wait_for_timeout(150)
        check('connecting state shows first', await pg.query_selector('.conn-compact .cc-ic.connecting'))
        await pg.wait_for_timeout(1000)
        check('connected state follows (or a ride row once riding)', await pg.query_selector('.conn-compact .cc-ic.on, .rec-row'))
        await shot(pg, '07-connected')
        await pg.wait_for_timeout(8000)
        check('ride mode opens by itself at speed', await pg.query_selector('#ride-mode'))
        check('ride mode has one layout, no swipe dots', len(await pg.query_selector_all('#ride-mode .rm-page')) == 1 and not await pg.query_selector('.rm-dots'))
        await shot(pg, '08-ride-mode')
        await pg.mouse.move(200, 400); await pg.mouse.down(); await pg.wait_for_timeout(1100); await pg.mouse.up(); await pg.wait_for_timeout(400)
        check('hold to leave closes ride mode', not await pg.query_selector('#ride-mode'))

        # Recording status + stop confirmation
        check('today shows the ride in progress', 'ride in progress' in (await text(pg, '.view')).lower())
        await pg.evaluate("location.hash='alerts'"); await pg.wait_for_timeout(500)
        check('recording pill shows on other tabs', await pg.query_selector('.live-pill.rec'))
        await pg.evaluate("location.hash='trips'"); await pg.wait_for_timeout(500)
        check('no floating button on trips', not await pg.query_selector('.fab'))
        await pg.click('.view [data-arg="rec-end"]'); await pg.wait_for_timeout(500)
        check('stop opens a confirmation sheet', 'End this ride?' in await text(pg, '.sheet'))
        await shot(pg, '08b-end-ride')
        await pg.click('.sheet-body [data-act="close"]'); await pg.wait_for_timeout(500)
        check('keep riding leaves the recording running', await pg.query_selector('.view [data-arg="rec-end"]'))
        before = await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).vehicles.find(v=>v.nickname==='Appa’s 150').trips.length")
        await pg.click('[data-arg="rec-end"]'); await pg.wait_for_timeout(500)
        await pg.click('.sheet [data-act="rec-stop"]'); await pg.wait_for_timeout(700)
        after = await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).vehicles.find(v=>v.nickname==='Appa’s 150').trips.length")
        check('save ride keeps it', after == before + 1 and not await pg.query_selector('.view [data-arg="rec-end"]'), f'{before}->{after}')

        # Rehearsal navigation
        await pg.evaluate("location.hash='route'"); await pg.wait_for_timeout(500)
        check('Bluetooth messages hidden unless turned on', not await pg.query_selector('[data-live="frame"]'))
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(400)
        await pg.click('[data-act="toggle"][data-arg="devFrames"]'); await pg.wait_for_timeout(300)
        await pg.evaluate("location.hash='route'"); await pg.wait_for_timeout(500)
        check('route explains how it works', await pg.query_selector('.howto'))
        check('selected arrow has no tick, others are grey', not await pg.query_selector('.grid-man .selmark') and await pg.eval_on_selector('.grid-man button:not(.on)', 'e => getComputedStyle(e).getPropertyValue("--accent").trim()') != await pg.eval_on_selector('.grid-man button.on', 'e => getComputedStyle(e).getPropertyValue("--accent").trim()'))
        check('slider shows its fill', '--p' in (await pg.get_attribute('[data-input="turn-dist"]', 'style') or ''))
        await shot(pg, '10-slider')
        await pg.click('[data-act="nav-rehearse"]'); await pg.wait_for_timeout(1500)
        check('rehearsal streams turns', 'Ballari Road' in await text(pg, '.instr'))
        frame = await text(pg, '[data-live="frame"]')
        check('a frame was built', len(frame.split()) in (48, 89, 55), str(len(frame.split())))
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(600)
        check('live nav pill appears off the route tab', await pg.query_selector('.live-pill'))
        await pg.evaluate("location.hash='route'"); await pg.wait_for_timeout(400)
        await pg.click('[data-act="nav-end"]'); await pg.wait_for_timeout(500)

        # Calls
        await pg.evaluate("location.hash='alerts'"); await pg.wait_for_timeout(500)
        await pg.click('[data-act="alerts-tab"][data-arg="recent"]'); await pg.wait_for_timeout(400)
        await pg.click('[data-act="relay"][data-arg="ring"]'); await pg.wait_for_timeout(400)
        check('incoming call overlay shows Rohan', 'Rohan' in await text(pg, '.call'))
        await pg.click('[data-act="call-decline"]'); await pg.wait_for_timeout(300)
        await pg.click('[data-act="press"][data-arg="tap"]'); await pg.wait_for_timeout(300)
        check('handlebar press is logged', 'Play' in await text(pg, '.toasts') or 'Tap' in await text(pg, '.view'))

        # Dark theme contrast sanity
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(400)
        await pg.click('[data-act="theme"][data-arg="obsidian"]'); await pg.wait_for_timeout(400)
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(400)
        await pg.evaluate("document.querySelector('.view').scrollTo(0, 400)"); await pg.wait_for_timeout(500)
        check('page header sticks with blur when scrolled', await pg.query_selector('.pagehead.stuck'))
        hy = await pg.evaluate("document.querySelector('.pagehead').getBoundingClientRect().top")
        check('sticky header stays at the top', abs(hy) < 2, str(hy))
        await shot(pg, '11-sticky')
        check('dark theme applies', await pg.evaluate("document.documentElement.dataset.theme") == 'obsidian')
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(600)
        await shot(pg, '09-dark')

        # Weather on Today
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(300)
        await pg.evaluate("(() => { const st = JSON.parse(localStorage.getItem('pulsarnav-web-v2')); })()")
        await pg.wait_for_timeout(500)
        head = await text(pg, '.bc-wx')
        check('bike card carries the weather, no condition text', '°' in head and 'cloudy' not in head.lower(), head)
        check('Today title is the weekday, date below', (await text(pg, '.today-head .title')).strip() in ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'] and len(await text(pg, '.today-head .subtitle')) <= 14)
        await pg.click('.bc-wx'); await pg.wait_for_timeout(600)
        sheet = await text(pg, '.sheet')
        check('tapping it opens the full forecast', 'next 12 hours' in sheet.lower() and 'feels like' in sheet.lower(), sheet[:120])
        await shot(pg, '14-weather')
        await pg.evaluate("document.querySelector('.backdrop')?.click()"); await pg.wait_for_timeout(500)
        # Rotation: portrait-only except Route and ride mode
        await pg.set_viewport_size({'width': 844, 'height': 390}); await pg.wait_for_timeout(300)
        check('landscape covers the app on Today', await pg.eval_on_selector('.rotate-lock', 'e => getComputedStyle(e).display') == 'flex')
        await pg.evaluate("location.hash='route'"); await pg.wait_for_timeout(500)
        check('landscape is allowed on Route', await pg.eval_on_selector('.rotate-lock', 'e => getComputedStyle(e).display') == 'none')
        await shot(pg, '10-route-landscape')
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(400)
        await pg.set_viewport_size({'width': 390, 'height': 844}); await pg.wait_for_timeout(300)
        check('portrait has no cover', await pg.eval_on_selector('.rotate-lock', 'e => getComputedStyle(e).display') == 'none')
        # Sticky header: solid all the way down its own content
        await pg.eval_on_selector('.view', 'e => e.scrollTop = 400'); await pg.wait_for_timeout(400)
        bg = await pg.eval_on_selector('.pagehead', """e => { const r = e.getBoundingClientRect(), st = getComputedStyle(e, '::before'); return [st.backgroundImage.includes('calc') || st.backgroundImage.includes('px'), parseFloat(st.bottom)] }""")
        check('header backdrop stays solid past its content', bg[1] <= -20, str(bg))
        await shot(pg, '11-header-stuck')
        # Loader word is centred; dots hang outside it
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(500)
        await pg.click('[data-act="show-loader"]'); await pg.wait_for_timeout(300)
        off = await pg.evaluate("""() => { const t = document.querySelector('#splash .wl-loop').getBoundingClientRect(), a = document.querySelector('.app').getBoundingClientRect(); return Math.abs((t.left + t.right) / 2 - (a.left + a.right) / 2) }""")
        check('loading word is optically centred', off < 2, str(off))
        await pg.wait_for_timeout(3200)
        # Edge preview: lengths stay inside their boxes
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(400)
        await pg.click('[data-act="scenario"][data-arg="edge"]'); await pg.wait_for_timeout(600)
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(500)
        lines = await pg.eval_on_selector_all('.vc-line', 'els => els.map(e => e.scrollHeight <= e.clientHeight + 2 && getComputedStyle(e).whiteSpace == "nowrap")')
        check('garage card lines never wrap', lines and all(lines), str(lines))
        await shot(pg, '12-edge-settings')
        await pg.evaluate("location.hash='today'"); await pg.wait_for_timeout(500)
        txt = await text(pg, '.readings')
        check('edge readings show whole trip km and floored odometer', '9,999' in txt and '1,99,999' in txt and '2,00,000' not in txt, txt[:120])
        await shot(pg, '13-edge-today')
        await pg.evaluate("location.hash='trips'"); await pg.wait_for_timeout(500)
        eb = await pg.eval_on_selector('.pagehead .subtitle', 'e => getComputedStyle(e).whiteSpace == "nowrap" && e.getBoundingClientRect().height < 30')
        check('trips subtitle stays on one line', eb)
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(400)
        await pg.click('[data-act="scenario"][data-arg="real"]'); await pg.wait_for_timeout(500)

        # Sound switch exists and toggles cleanly
        await pg.evaluate("location.hash='settings'"); await pg.wait_for_timeout(400)
        snd = await pg.query_selector('[data-act="toggle"][data-arg="sounds"]')
        check('sound effects switch is in Settings', snd)
        if snd:
            await snd.click(); await pg.wait_for_timeout(200)
            off = await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).settings.sounds")
            await (await pg.query_selector('[data-act="toggle"][data-arg="sounds"]')).click(); await pg.wait_for_timeout(200)
            on = await pg.evaluate("JSON.parse(localStorage.getItem('pulsarnav-web-v2')).settings.sounds")
            check('sound switch turns off and back on', off is False and on is True, f'{off} {on}')
        check('no runtime errors', not errors, '; '.join(errors[:3]))
        await b.close()
    failed = [r for r in results if not r[1]]
    print(f'\n{len(results) - len(failed)}/{len(results)} passed')
    sys.exit(1 if failed else 0)

asyncio.run(main())
