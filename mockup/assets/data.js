/*
 * Mock data and a tiny in-browser simulation that stands in for the live WebSocket feed.
 * Shapes follow the canonical Position / DeviceEvent contracts in libs/tracker-model.
 * Routes are hand-drawn waypoint lists across London (they cut corners; it's a mockup).
 */
(function () {
  "use strict";

  const P = (lat, lng) => ({ lat, lng });

  const drivers = [
    { id: "d1", name: "Amara Okafor",    employeeId: "EMP-1042", phone: "+44 7700 900123", email: "amara.okafor@example.com", licence: "OKAFO902135AM9IJ", licenceExpiry: "2029-03-14", licenceClass: "B, C1", joined: "2021-05-03", rating: 4.8, color: "#0B4F8A" },
    { id: "d2", name: "Tomasz Nowak",    employeeId: "EMP-1088", phone: "+44 7700 900456", email: "tomasz.nowak@example.com", licence: "NOWAK811027TN4KL", licenceExpiry: "2027-11-02", licenceClass: "B, C, CE", joined: "2019-09-16", rating: 4.6, color: "#6B3FA0" },
    { id: "d3", name: "Priya Raman",     employeeId: "EMP-1121", phone: "+44 7700 900789", email: "priya.raman@example.com", licence: "RAMAN856214PR7QX", licenceExpiry: "2031-06-21", licenceClass: "B", joined: "2023-02-01", rating: 4.9, color: "#1E7A70" },
    { id: "d4", name: "Daniel Mensah",   employeeId: "EMP-1003", phone: "+44 7700 900234", email: "daniel.mensah@example.com", licence: "MENSA790318DM2AB", licenceExpiry: "2028-01-09", licenceClass: "B, C1", joined: "2018-04-23", rating: 4.5, color: "#A0522D" },
    { id: "d5", name: "Siobhan Byrne",   employeeId: "EMP-1067", phone: "+44 7700 900567", email: "siobhan.byrne@example.com", licence: "BYRNE877409SB5CD", licenceExpiry: "2030-08-30", licenceClass: "B, C, CE", joined: "2020-11-11", rating: 4.7, color: "#2F6B2F" },
    { id: "d6", name: "Kwame Asante",    employeeId: "EMP-1150", phone: "+44 7700 900890", email: "kwame.asante@example.com", licence: "ASANT920512KA8EF", licenceExpiry: "2032-05-12", licenceClass: "B", joined: "2024-07-08", rating: 4.4, color: "#8A3A5A" },
    { id: "d7", name: "Laura Fernández", employeeId: "EMP-1099", phone: "+44 7700 900345", email: "laura.fernandez@example.com", licence: "FERNA884120LF3GH", licenceExpiry: "2029-12-01", licenceClass: "B, C1", joined: "2022-03-14", rating: 4.8, color: "#3D5A80" },
    { id: "d8", name: "Oliver Hughes",   employeeId: "EMP-1031", phone: "+44 7700 900678", email: "oliver.hughes@example.com", licence: "HUGHE751230OH6IJ", licenceExpiry: "2026-12-30", licenceClass: "B", joined: "2017-08-21", rating: 4.3, color: "#5C5C5C" }
  ];

  const routes = {
    paddingtonToCanaryWharf: [P(51.5154,-0.1755),P(51.5200,-0.1630),P(51.5226,-0.1545),P(51.5250,-0.1385),P(51.5275,-0.1310),P(51.5300,-0.1230),P(51.5317,-0.1100),P(51.5290,-0.0980),P(51.5265,-0.0880),P(51.5235,-0.0790),P(51.5180,-0.0720),P(51.5130,-0.0590),P(51.5110,-0.0420),P(51.5080,-0.0300),P(51.5050,-0.0195)],
    heathrowToParkRoyal:     [P(51.4700,-0.4543),P(51.4810,-0.4400),P(51.4870,-0.4100),P(51.4900,-0.3700),P(51.4930,-0.3300),P(51.4960,-0.3000),P(51.5000,-0.2780),P(51.5150,-0.2650),P(51.5280,-0.2650)],
    kingsCrossToGreenwich:   [P(51.5308,-0.1238),P(51.5250,-0.1180),P(51.5175,-0.1100),P(51.5110,-0.1030),P(51.5060,-0.0880),P(51.5000,-0.0780),P(51.4930,-0.0600),P(51.4880,-0.0400),P(51.4820,-0.0150),P(51.4800,-0.0050)],
    brixtonToWembley:        [P(51.4613,-0.1156),P(51.4720,-0.1230),P(51.4850,-0.1350),P(51.4950,-0.1450),P(51.5020,-0.1600),P(51.5100,-0.1800),P(51.5250,-0.2000),P(51.5400,-0.2200),P(51.5520,-0.2600),P(51.5560,-0.2795)],
    stratfordToCroydon:      [P(51.5416,-0.0034),P(51.5300,-0.0200),P(51.5150,-0.0300),P(51.5000,-0.0450),P(51.4800,-0.0600),P(51.4550,-0.0750),P(51.4300,-0.0850),P(51.4050,-0.0950),P(51.3800,-0.1000),P(51.3727,-0.1000)],
    batterseaDepot:          [P(51.4795,-0.1445),P(51.4800,-0.1440)],
    camdenToIslington:       [P(51.5390,-0.1426),P(51.5380,-0.1300),P(51.5360,-0.1050)],
    victoriaToHampstead:     [P(51.4952,-0.1441),P(51.5030,-0.1500),P(51.5110,-0.1570),P(51.5200,-0.1600),P(51.5300,-0.1640),P(51.5420,-0.1700),P(51.5500,-0.1720),P(51.5560,-0.1780)]
  };

  // status: moving | idle (engine on, not moving) | stopped (ignition off) | offline (no data)
  const vehicles = [
    { id: "v1", name: "Van 12",   plate: "LK21 HXT", type: "Van",   make: "Ford Transit Custom",  group: "Deliveries North", driverId: "d1", status: "moving",  baseSpeed: 38, progress: .32, fuel: 62, power: 13.8,
      device: { imei: "359000000000015", protocol: "Teltonika", model: "FMC130" },
      trip: { id: "T-24117", startedMinAgo: 46, from: "Paddington Basin, W2 1JU", to: "Canary Wharf, E14 5AB", route: routes.paddingtonToCanaryWharf } },
    { id: "v2", name: "Truck 03", plate: "BV19 KMR", type: "Truck", make: "DAF LF 7.5t",          group: "Freight West",     driverId: "d2", status: "moving",  baseSpeed: 52, progress: .55, fuel: 41, power: 27.6,
      device: { imei: "359000000000023", protocol: "GT06", model: "Concox GT06N" },
      trip: { id: "T-24109", startedMinAgo: 71, from: "Heathrow Cargo Centre, TW6 2JS", to: "Park Royal Estate, NW10 7XP", route: routes.heathrowToParkRoyal } },
    { id: "v3", name: "Car 07",   plate: "YH72 PLD", type: "Car",   make: "Toyota Corolla Hybrid", group: "Field Engineers",  driverId: "d3", status: "moving",  baseSpeed: 34, progress: .18, fuel: 77, power: 14.1,
      device: { imei: "359000000000031", protocol: "OsmAnd", model: "Traccar Client (Android)" },
      trip: { id: "T-24121", startedMinAgo: 19, from: "King's Cross, N1C 4QP", to: "Greenwich Peninsula, SE10 0DX", route: routes.kingsCrossToGreenwich } },
    { id: "v4", name: "Van 15",   plate: "LR70 WSE", type: "Van",   make: "Mercedes Sprinter",     group: "Deliveries South", driverId: "d4", status: "moving",  baseSpeed: 31, progress: .62, fuel: 28, power: 13.6,
      device: { imei: "359000000000049", protocol: "H02", model: "Sinotrack ST-901" },
      trip: { id: "T-24104", startedMinAgo: 58, from: "Brixton Hill, SW2 1RW", to: "Wembley Park, HA9 0WS", route: routes.brixtonToWembley } },
    { id: "v5", name: "Truck 08", plate: "GN68 TCU", type: "Truck", make: "Volvo FL 12t",          group: "Freight East",     driverId: "d5", status: "stopped", baseSpeed: 44, progress: .48, fuel: 55, power: 25.9, stoppedTicks: 45,
      device: { imei: "359000000000056", protocol: "Teltonika", model: "FMB920" },
      trip: { id: "T-24112", startedMinAgo: 83, from: "Stratford City, E20 1EJ", to: "Purley Way, Croydon CR0 4RQ", route: routes.stratfordToCroydon } },
    { id: "v6", name: "Van 21",   plate: "LC22 FZA", type: "Van",   make: "Renault Master",        group: "Deliveries South", driverId: "d6", status: "idle",    baseSpeed: 0,  progress: .5,  fuel: 90, power: 13.9, parked: true,
      device: { imei: "359000000000064", protocol: "GT06", model: "Concox JM-VL01" },
      trip: { id: "T-24123", startedMinAgo: 6, from: "Battersea Depot, SW11 8AL", to: "Loading bay 4 (waiting)", route: routes.batterseaDepot } },
    { id: "v7", name: "Car 02",   plate: "EY17 OBN", type: "Car",   make: "Škoda Octavia Estate",  group: "Field Engineers",  driverId: "d7", status: "offline", baseSpeed: 0,  progress: .4,  fuel: 34, power: 0, offlineMinAgo: 132, parked: true,
      device: { imei: "359000000000072", protocol: "Queclink", model: "GV300" },
      trip: { id: "T-24088", startedMinAgo: 160, from: "Camden Town, NW1 7JR", to: "Upper Street, Islington N1 1QP", route: routes.camdenToIslington } },
    { id: "v8", name: "Car 11",   plate: "MA23 RVK", type: "Car",   make: "Kia Niro EV",           group: "Field Engineers",  driverId: "d8", status: "moving",  baseSpeed: 29, progress: .41, fuel: 64, power: 14.0,
      device: { imei: "359000000000080", protocol: "Teltonika", model: "FMC003" },
      trip: { id: "T-24119", startedMinAgo: 27, from: "Victoria Station, SW1V 1JU", to: "Hampstead Heath, NW3 1BN", route: routes.victoriaToHampstead } }
  ];

  // ---------- geometry ----------
  const R = 6371000;
  const rad = d => d * Math.PI / 180;
  function distance(a, b) {
    const dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
  }
  function bearing(a, b) {
    const y = Math.sin(rad(b.lng - a.lng)) * Math.cos(rad(b.lat));
    const x = Math.cos(rad(a.lat)) * Math.sin(rad(b.lat)) - Math.sin(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.cos(rad(b.lng - a.lng));
    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
  }
  function measure(route) {
    const cum = [0];
    for (let i = 1; i < route.length; i++) cum.push(cum[i - 1] + distance(route[i - 1], route[i]));
    return cum;
  }
  /** Point, heading and the travelled part of a route at distance d (metres). */
  function along(route, cum, d) {
    const total = cum[cum.length - 1];
    d = Math.max(0, Math.min(total, d));
    let i = 1;
    while (i < cum.length - 1 && cum[i] < d) i++;
    const a = route[i - 1], b = route[i];
    const seg = cum[i] - cum[i - 1] || 1;
    const t = (d - cum[i - 1]) / seg;
    const pos = P(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t);
    return { pos, course: bearing(a, b), travelled: [...route.slice(0, i), pos], remaining: [pos, ...route.slice(i)] };
  }

  // ---------- runtime state ----------
  const now = () => Date.now();
  const byId = (list, id) => list.find(x => x.id === id);

  vehicles.forEach(v => {
    v.driver = byId(drivers, v.driverId);
    v.trip.cum = measure(v.trip.route);
    v.trip.totalM = v.trip.cum[v.trip.cum.length - 1];
    v.trip.startedAt = new Date(now() - v.trip.startedMinAgo * 60000);
    v.trip.start = v.trip.route[0];
    v.trip.end = v.trip.route[v.trip.route.length - 1];
    v.distM = v.trip.totalM * v.progress;
    v.speed = v.status === "moving" ? v.baseSpeed : 0;
    v.ignition = v.status === "moving" || v.status === "idle";
    v.lastUpdate = v.status === "offline" ? new Date(now() - v.offlineMinAgo * 60000) : new Date();
    v.odometerKm = 20000 + Math.round(Math.random() * 60000);
    Object.assign(v, along(v.trip.route, v.trip.cum, v.distM));
    const avgKph = Math.max(18, v.baseSpeed * 0.7);
    v.trip.etaMin = Math.round((v.trip.totalM - v.distM) / 1000 / avgKph * 60);
  });

  // ---------- simulation (stands in for the Live Hub WebSocket) ----------
  const listeners = new Set();
  const speedUp = (window.VT_CONFIG && window.VT_CONFIG.simulationSpeed) || 8;

  function tick() {
    const t = new Date();
    vehicles.forEach(v => {
      if (v.status === "offline" || v.parked) {
        if (v.status === "idle") v.lastUpdate = t;
        return;
      }
      if (v.status === "stopped") {
        if (--v.stoppedTicks <= 0) { v.status = "moving"; v.ignition = true; }
        else { v.speed = 0; v.lastUpdate = t; return; }
      }
      if (v.status === "idle") {                 // stuck in traffic
        if (--v.idleTicks <= 0) v.status = "moving"; else { v.speed = 0; v.lastUpdate = t; return; }
      }
      if (Math.random() < 0.012) { v.status = "idle"; v.idleTicks = 4 + Math.floor(Math.random() * 8); v.speed = 0; v.lastUpdate = t; return; }
      const target = v.baseSpeed * (0.7 + Math.random() * 0.6);
      v.speed = Math.max(8, Math.round(v.speed + (target - v.speed) * 0.4));
      v.distM += v.speed / 3.6 * speedUp;
      if (v.distM >= v.trip.totalM) {           // arrived: start the trip again for the demo
        v.distM = 0;
        v.trip.startedAt = t;
      }
      Object.assign(v, along(v.trip.route, v.trip.cum, v.distM));
      v.fuel = Math.max(5, v.fuel - 0.01);
      v.lastUpdate = t;
      const avgKph = Math.max(18, v.baseSpeed * 0.7);
      v.trip.etaMin = Math.round((v.trip.totalM - v.distM) / 1000 / avgKph * 60);
    });
    listeners.forEach(fn => fn(vehicles));
  }
  let timer = null;
  function subscribe(fn) {
    listeners.add(fn);
    if (!timer) timer = setInterval(tick, 1000);
    return () => listeners.delete(fn);
  }

  // ---------- completed trips (trip history) ----------
  function fmtTime(d) { return d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" }); }
  function buildTrips() {
    const trips = [];
    const midnight = new Date(); midnight.setHours(0, 0, 0, 0);
    const plan = [
      ["v1", 7.4,  routes.paddingtonToCanaryWharf.slice().reverse(), "Canary Wharf, E14 5AB", "Paddington Basin, W2 1JU"],
      ["v2", 6.9,  routes.heathrowToParkRoyal.slice().reverse(),     "Park Royal Estate, NW10 7XP", "Heathrow Cargo Centre, TW6 2JS"],
      ["v3", 8.1,  routes.kingsCrossToGreenwich.slice().reverse(),   "Greenwich Peninsula, SE10 0DX", "King's Cross, N1C 4QP"],
      ["v4", 7.8,  routes.brixtonToWembley.slice().reverse(),        "Wembley Park, HA9 0WS", "Brixton Hill, SW2 1RW"],
      ["v5", 6.2,  routes.stratfordToCroydon.slice().reverse(),      "Purley Way, Croydon CR0 4RQ", "Stratford City, E20 1EJ"],
      ["v7", 8.6,  routes.camdenToIslington.slice().reverse(),       "Upper Street, Islington N1 1QP", "Camden Town, NW1 7JR"],
      ["v8", 7.9,  routes.victoriaToHampstead.slice().reverse(),     "Hampstead Heath, NW3 1BN", "Victoria Station, SW1V 1JU"],
      ["v1", 6.5,  routes.kingsCrossToGreenwich,                     "King's Cross, N1C 4QP", "Greenwich Peninsula, SE10 0DX"]
    ];
    plan.forEach(([vid, startHour, route, from, to], i) => {
      const v = byId(vehicles, vid);
      const cum = measure(route);
      const totalM = cum[cum.length - 1];
      const avgKph = Math.max(20, (v.baseSpeed || 30) * 0.75);
      const stopCount = Math.min(3, Math.max(1, Math.round(route.length / 4)));
      const stops = [];
      for (let s = 1; s <= stopCount; s++) {
        const idx = Math.round(route.length * s / (stopCount + 1));
        stops.push({ pos: route[Math.min(idx, route.length - 2)], minutes: 3 + ((i + s) * 7) % 12 });
      }
      const stopMin = stops.reduce((a, s) => a + s.minutes, 0);
      const driveMin = Math.round(totalM / 1000 / avgKph * 60);
      const start = new Date(midnight.getTime() + startHour * 3600000);
      const end = new Date(start.getTime() + (driveMin + stopMin) * 60000);
      const maxSpeed = Math.round(avgKph * 1.55);
      const events = [
        { at: start, text: "Ignition on, trip started" },
        { at: new Date(start.getTime() + driveMin * 0.3 * 60000), text: `Stopped ${stops[0].minutes} min` },
        { at: new Date(start.getTime() + driveMin * 0.55 * 60000), text: `Speeding: ${maxSpeed} km/h in a 50 km/h zone`, warn: maxSpeed > 55 },
        { at: new Date(end.getTime() - 60000), text: "Arrived at destination" },
        { at: end, text: "Ignition off, trip ended" }
      ];
      trips.push({
        id: "T-" + (24050 + i * 7), vehicle: v, driver: v.driver, route, cum, totalM,
        from, to, start, end, startPos: route[0], endPos: route[route.length - 1],
        distanceKm: totalM / 1000, durationMin: driveMin + stopMin, avgKph: Math.round(avgKph), maxSpeed,
        idleMin: Math.round(stopMin * 0.4) + 2, stops, events
      });
    });
    return trips.sort((a, b) => b.start - a.start);
  }

  window.VT = {
    drivers, vehicles, routes,
    trips: buildTrips(),
    geo: { distance, bearing, measure, along },
    subscribe,
    byId: id => byId(vehicles, id),
    fmtTime
  };
})();
