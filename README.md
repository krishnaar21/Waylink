# Waylink

**Your bike's display, connected.** Waylink is a free companion app for Bajaj motorcycles with the Bluetooth digital cluster (Pulsar and Dominar). It sends turn arrows, caller names and the time to the cluster, and keeps your bike's care in one place: rides, fuel, service reminders, documents and an SOS card.

It's a web app you install to your Home Screen. No account, no ads, no tracking. Everything stays on your phone.

**Open it:** https://krishnaar21.github.io/Waylink/ → *Share* → *Add to Home Screen* (iPhone) or *Install app* (Android, Chrome).

---

## What it does

| Tab | What you get |
|---|---|
| **Today** | Your bike at a glance: link status, a service verdict (on track, due soon, overdue), weather for the ride, missed calls, where you parked, and live readings (odometer, trips, speed, fuel, range). |
| **Route** | Plan in Google Maps, rehearse a route, or send a single test turn to see how arrows look on the cluster. |
| **Rides** | Record rides with your phone's GPS (or by themselves when the bike links). A swipeable chart of your riding: a 12-week calendar, km per week, or km by weekday. |
| **Alerts** | Missed calls and WhatsApp alerts sent to the bike, and the things that need you (overdue service, expiring documents). Only names and counts reach the bike, never message text. |
| **Settings** | Profile and Medical ID, your garage of bikes, riding options, look and sound, backup and restore. |

Also inside: **ride mode** (a big speedometer screen for the handlebar mount), a **fuel log** with mileage worked out full-tank to full-tank, a **service logbook** with reminders by km or date, a **glovebox** for licence, insurance and PUC with expiry reminders, and an **SOS screen** with your blood group, allergies and emergency contact.

### Supported bikes
Pulsar NS200, RS200, NS400Z, NS160, NS125, N250 / F250, N160 / N150, 150 / 125, 220F, and Dominar 400 and 250 — any of these with the Bluetooth cluster (the one that can show calls). The app lists them under *Settings → About Waylink → Bike models*.

### Pairing
- **Android:** Chrome pairs directly. Tap *Pair* on Today with the ignition on.
- **iPhone:** Safari can't use Bluetooth from a web page. Waylink walks you through opening it in the free **Bluefy** browser, which can.
- **No bike handy?** *Try the simulated bike* runs everything without one.

A web page can't read Google Maps' own turn alerts, so live turns from Maps need the Android app. In the browser you can rehearse routes and send test turns.

### Privacy
All data stays on the phone, in the browser's storage. Weather and the place lookup send only your rough location to Open-Meteo and OpenStreetMap. Use *Settings → Backup & restore* to move to a new phone.

---

## For developers

Plain HTML, CSS and ES modules. No framework, no build step.

```
python3 -m http.server 8765      # then open http://localhost:8765
```

### Layout
| Path | What's there |
|---|---|
| `index.html` | Shell, splash, boot safety net, theme painted before load |
| `styles.css` | All styles; themes are CSS variables (`data-theme`, `data-accent`, `data-night`) |
| `js/app.js` | State, actions (`A`), forms (`FORMS`), rendering, gestures |
| `js/views.js` | Every screen and sheet as template strings |
| `js/store.js` | Data model, bike models, migrations, sample data |
| `js/link.js`, `js/protocol.js` | Web Bluetooth link and the cluster's byte protocol |
| `js/sound.js`, `js/icons-huge.js` | UI sounds and the icon set |
| `sw.js` | Offline cache. **Bump `CACHE` on every release** so phones pick up the change |
| `img/` | Onboarding art, avatars, logos, splash screens |
| `tools/` | Scripts that generate the art (avatars, onboarding scenes, the bike's colours, icons) |

### Themes
Riders see **Auto**, **Paper** and **Paper dark**; Auto follows the phone's light or dark setting. Light, Dark and Navy are still in the code, behind *Settings → Developer → Show all themes*.

### Tests
```
node --test tests/*.test.mjs     # unit and edge cases
python3 tests/e2e.py             # browser run-through (needs Playwright and Chrome, and the server on :8765)
```

### Releasing
Pushing to `main` deploys to GitHub Pages through `.github/workflows/pages.yml` (repo *Settings → Pages → Source: GitHub Actions*). Bump `CACHE` in `sw.js` first.

### Bluetooth protocol
The cluster speaks OTC Engineering's protocol: one service with characteristics for turn-by-turn (48 bytes), a status heartbeat (89 bytes), missed calls, WhatsApp alerts and the handlebar buttons. `js/protocol.js` is a byte-for-byte port of the PulsarNav Android app; don't change layouts there without checking on a real cluster.

---

## Credits
Bluetooth protocol from [PulsarNav](https://github.com/nikhilmaity00/pulsarnav) · icons by [Hugeicons](https://hugeicons.com) · weather by [Open-Meteo](https://open-meteo.com) · places by [OpenStreetMap Nominatim](https://nominatim.openstreetmap.org).

Not affiliated with Bajaj Auto, OTC, Google or WhatsApp. Names and marks belong to their owners.

Support: support@krishnaar.work · MIT licence.
