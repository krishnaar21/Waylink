# Waylink

A companion web app for Bajaj Bluetooth clusters (Pulsar, Dominar): turn-by-turn arrows and caller names on the bike, plus rides, fuel, service reminders, weather and a digital glovebox. Everything stays on your phone.

**Use it:** open the GitHub Pages link in Safari or Chrome, then *Add to Home Screen*.

## Run locally
```
python3 -m http.server 8765
```
Then open http://localhost:8765. No build step; plain ES modules.

## Tests
```
node --test tests/*.test.mjs        # unit and edge cases
python3 tests/e2e.py                 # browser checks (needs Playwright and Chrome)
```

## Publish
Pushing to `main` deploys to GitHub Pages through `.github/workflows/pages.yml`
(repo Settings → Pages → Source: **GitHub Actions**).

## Credits
Bluetooth protocol from PulsarNav · icons: Hugeicons (https://hugeicons.com, MIT) · weather by Open-Meteo · places by OpenStreetMap Nominatim.
Not affiliated with Bajaj Auto, OTC, Google or WhatsApp. MIT licence.
