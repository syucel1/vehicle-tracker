# Management console mockups

Clickable HTML mockups of the web management application: sign-in, live tracking of the whole fleet, a selected vehicle with its driver and trip start/end points, trip history with playback, and fleet administration. No build step, no backend: vehicles are simulated in the browser.

| Page | What it shows |
|---|---|
| [`index.html`](index.html) | Sign in (any details work; the product will redirect to Keycloak) |
| [`live-map.html`](live-map.html) | All vehicles moving on the map at once, filters by status, group and search. Select a vehicle (list or map) to see its driver, current trip from start point **A** to destination **B**, travelled and remaining route, speed, ignition, fuel and tracker details. Options: show all vehicles, follow the selected vehicle, plate labels, traffic (Google only). |
| [`trips.html`](trips.html) | Today's completed trips: start and end points, stops, driver, statistics, events, and route playback |
| [`fleet.html`](fleet.html) | Vehicles, drivers and tracking devices tables, plus an "Add vehicle" form that shows which gateway port to configure on the tracker |

## Run it

Serve the folder over HTTP (Google Maps keys are normally restricted to a website address):

```bash
cd mockup
python -m http.server 8080        # or: npx serve -l 8080
```

Open <http://localhost:8080>.

## Google Maps

1. In Google Cloud Console, enable the **Maps JavaScript API** and create an API key.
2. Restrict the key to `http://localhost:8080/*` (or wherever you serve the mockup).
3. Put it in [`assets/config.js`](assets/config.js):

```js
window.VT_CONFIG = { googleMapsApiKey: "YOUR_KEY", googleMapId: "DEMO_MAP_ID", simulationSpeed: 8 };
```

`DEMO_MAP_ID` is Google's development Map ID for Advanced Markers; create your own Map ID for custom map styling.

Without a key (or if Google can't be reached) the pages fall back to a simple **schematic map** so the mockup still works offline. Both maps sit behind the same small adapter ([`assets/map.js`](assets/map.js)), the "any map" approach from the system design (section 10.2): the pages never call a map SDK directly.

## Files

```
mockup/
├── index.html, live-map.html, trips.html, fleet.html
└── assets/
    ├── config.js   Google Maps key and simulation speed
    ├── styles.css  visual language: UK road signage (motorway blue, number-plate yellow, Barlow type)
    ├── data.js     mock vehicles, drivers, routes, trips + the in-browser simulation
    ├── map.js      map adapter: Google Maps (Advanced Markers) and schematic fallback
    └── app.js      top bar, formatting, driver card, toast
```

## Notes and limits

- Data follows the canonical `Position` / `DeviceEvent` contracts in `libs/tracker-model`; in the real app, live updates come from the Live Hub WebSocket instead of `VT.subscribe` in `data.js`.
- Routes are hand-drawn waypoint lines across London and cut through buildings; real tracks will follow roads.
- Names, phone numbers (Ofcom drama range 07700 900xxx), plates and IMEIs are fictitious.
- Buttons with no backend behind them (send message, trip report download, date range) show a short notice instead.
