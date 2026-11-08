/*
 * Map adapter, as described in the design document (section 10.2): the pages talk to this small
 * interface, never to a map SDK directly. Two implementations:
 *   - GoogleMapView: Google Maps JavaScript API with Advanced Markers (needs an API key)
 *   - SchematicMapView: dependency-free fallback used when no key is configured or Google fails
 *
 *   VTMap.create(element, { center, zoom }) -> Promise<view>
 *   view.addVehicle(vehicle, { selected, onClick }) -> { update(vehicle, { selected }), remove() }
 *   view.addPin(position, { kind: "start" | "end" | "stop", label, title }) -> { remove() }
 *   view.addPath(points, { color, weight, dashed }) -> { setPath(points), remove() }
 *   view.fitBounds(points, paddingPx)   view.panTo(position)   view.setTraffic(on)   view.kind
 */
(function () {
  "use strict";
  const cfg = window.VT_CONFIG || {};

  // ---------- shared marker elements ----------
  const ARROW = '<svg class="vmk-arrow" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5 19 20l-7-4-7 4z"/></svg>';
  function vehicleElement(v) {
    const el = document.createElement("div");
    el.className = "vmk";
    el.innerHTML = `<span class="vmk-dot">${ARROW}</span><span class="vmk-plate plate">${v.plate}</span>`;
    el.setAttribute("role", "button");
    el.tabIndex = 0;
    return el;
  }
  function styleVehicle(el, v, selected) {
    el.dataset.status = v.status;
    el.classList.toggle("is-selected", !!selected);
    el.setAttribute("aria-label", `${v.plate}, ${v.name}, ${v.status}`);
    el.querySelector(".vmk-arrow").style.transform = `rotate(${Math.round(v.course || 0)}deg)`;
  }
  function pinElement(kind, label) {
    if (kind === "stop") {
      const el = document.createElement("div");
      el.className = "stop-mk";
      el.textContent = label || "";
      return el;
    }
    const color = kind === "start" ? "var(--start)" : "var(--end)";
    const el = document.createElement("div");
    el.className = "pin-mk";
    el.innerHTML = `<svg viewBox="0 0 30 38" aria-hidden="true"><path d="M15 37C15 37 2 22.5 2 14a13 13 0 0 1 26 0c0 8.5-13 23-13 23z" fill="${color}" stroke="#fff" stroke-width="2"/></svg><span>${label || (kind === "start" ? "A" : "B")}</span>`;
    return el;
  }

  // ---------- Google Maps ----------
  let googlePromise = null;
  function loadGoogle() {
    if (googlePromise) return googlePromise;
    googlePromise = new Promise((resolve, reject) => {
      if (!cfg.googleMapsApiKey) { reject(new Error("no-key")); return; }
      const timeout = setTimeout(() => reject(new Error("timeout")), 10000);
      window.__vtMapsReady = () => { clearTimeout(timeout); resolve(window.google.maps); };
      const params = new URLSearchParams({ key: cfg.googleMapsApiKey, v: "weekly", libraries: "marker", loading: "async", callback: "__vtMapsReady" });
      const s = document.createElement("script");
      s.src = "https://maps.googleapis.com/maps/api/js?" + params;
      s.async = true;
      s.onerror = () => { clearTimeout(timeout); reject(new Error("load-failed")); };
      document.head.append(s);
    });
    return googlePromise;
  }

  class GoogleMapView {
    constructor(el, maps, opts) {
      this.kind = "google";
      this.maps = maps;
      this.map = new maps.Map(el, {
        center: opts.center, zoom: opts.zoom || 11, mapId: cfg.googleMapId || "DEMO_MAP_ID",
        disableDefaultUI: true, zoomControl: true, fullscreenControl: true, clickableIcons: false,
        gestureHandling: "greedy"
      });
      this.traffic = new maps.TrafficLayer();
    }
    addVehicle(v, { selected, onClick } = {}) {
      const el = vehicleElement(v);
      el.classList.add("vmk--google");
      styleVehicle(el, v, selected);
      const m = new this.maps.marker.AdvancedMarkerElement({ map: this.map, position: v.pos, content: el, title: v.plate, zIndex: selected ? 1000 : 1 });
      if (onClick) {
        el.addEventListener("click", e => { e.stopPropagation(); onClick(v); });
        el.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onClick(v); } });
      }
      return {
        update: (nv, o = {}) => { m.position = nv.pos; m.zIndex = o.selected ? 1000 : 1; styleVehicle(el, nv, o.selected); },
        remove: () => { m.map = null; }
      };
    }
    addPin(pos, { kind, label, title } = {}) {
      const el = pinElement(kind, label);
      const m = new this.maps.marker.AdvancedMarkerElement({ map: this.map, position: pos, content: el, title: title || "", zIndex: kind === "stop" ? 500 : 900 });
      return { remove: () => { m.map = null; } };
    }
    addPath(points, { color = "#0B4F8A", weight = 5, dashed = false } = {}) {
      const line = new this.maps.Polyline({
        map: this.map, path: points, strokeColor: color, strokeWeight: weight, geodesic: false,
        strokeOpacity: dashed ? 0 : 0.9,
        icons: dashed ? [{ icon: { path: "M 0,-1 0,1", strokeOpacity: 0.9, strokeWeight: weight - 1, scale: 3 }, offset: "0", repeat: "14px" }] : []
      });
      return { setPath: p => line.setPath(p), remove: () => line.setMap(null) };
    }
    fitBounds(points, padding = 60) {
      if (!points.length) return;
      const b = new this.maps.LatLngBounds();
      points.forEach(p => b.extend(p));
      this.map.fitBounds(b, padding);
    }
    panTo(pos) { this.map.panTo(pos); }
    setTraffic(on) { this.traffic.setMap(on ? this.map : null); }
  }

  // ---------- Schematic fallback ----------
  const LONDON_AREAS = [
    ["Westminster", 51.4975, -0.1357], ["City of London", 51.5155, -0.0922], ["Camden", 51.5390, -0.1426],
    ["Islington", 51.5465, -0.1058], ["Southwark", 51.5035, -0.0804], ["Lambeth", 51.4860, -0.1180],
    ["Greenwich", 51.4826, -0.0077], ["Stratford", 51.5416, -0.0034], ["Croydon", 51.3762, -0.0982],
    ["Ealing", 51.5130, -0.3089], ["Heathrow", 51.4700, -0.4543], ["Wembley", 51.5560, -0.2795],
    ["Hampstead", 51.5560, -0.1780], ["Canary Wharf", 51.5054, -0.0235], ["Brixton", 51.4613, -0.1156]
  ];
  const THAMES = [[51.486,-0.300],[51.470,-0.255],[51.480,-0.215],[51.468,-0.185],[51.481,-0.160],[51.486,-0.128],[51.501,-0.121],[51.509,-0.107],[51.508,-0.080],[51.505,-0.060],[51.507,-0.035],[51.497,-0.012],[51.505,0.005],[51.502,0.035]];

  const SVGNS = "http://www.w3.org/2000/svg";
  const mercX = lng => (lng + 180) / 360;
  const mercY = lat => { const s = Math.sin(lat * Math.PI / 180); return 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI); };

  class SchematicMapView {
    constructor(el, opts) {
      this.kind = "schematic";
      this.el = el;
      el.classList.add("schematic");
      el.innerHTML = "";
      this.svg = document.createElementNS(SVGNS, "svg");
      this.svg.classList.add("layer-paths");
      this.markers = document.createElement("div");
      this.markers.className = "layer-markers";
      el.append(this.svg, this.markers);
      this.items = new Set();
      this.scale = 1; this.cx = mercX(opts.center.lng); this.cy = mercY(opts.center.lat);
      this.scale = 256 * Math.pow(2, opts.zoom || 11);

      // background: river + area names (helps orientation without real tiles)
      const river = document.createElementNS(SVGNS, "path");
      river.classList.add("river");
      this.svg.append(river);
      this.items.add({ draw: () => river.setAttribute("d", this.pathD(THAMES.map(([lat, lng]) => ({ lat, lng })))) });
      LONDON_AREAS.forEach(([name, lat, lng]) => {
        const l = document.createElement("div");
        l.className = "area-label"; l.textContent = name;
        this.markers.append(l);
        this.items.add({ draw: () => this.place(l, { lat, lng }) });
      });

      // zoom buttons + drag + wheel
      const zoom = document.createElement("div");
      zoom.className = "zoom";
      zoom.innerHTML = '<button type="button" aria-label="Zoom in">+</button><button type="button" aria-label="Zoom out">−</button>';
      zoom.children[0].onclick = () => this.zoomBy(1.5);
      zoom.children[1].onclick = () => this.zoomBy(1 / 1.5);
      el.append(zoom);
      el.addEventListener("wheel", e => { e.preventDefault(); this.zoomBy(e.deltaY < 0 ? 1.2 : 1 / 1.2); }, { passive: false });
      let drag = null;
      el.addEventListener("pointerdown", e => {
        if (e.target.closest(".vmk, .zoom, button")) return;
        drag = { x: e.clientX, y: e.clientY, cx: this.cx, cy: this.cy };
        el.classList.add("is-dragging"); el.setPointerCapture(e.pointerId);
      });
      el.addEventListener("pointermove", e => {
        if (!drag) return;
        this.cx = drag.cx - (e.clientX - drag.x) / this.scale;
        this.cy = drag.cy - (e.clientY - drag.y) / this.scale;
        this.redraw();
      });
      const end = () => { drag = null; el.classList.remove("is-dragging"); };
      el.addEventListener("pointerup", end); el.addEventListener("pointercancel", end);
      new ResizeObserver(() => this.redraw()).observe(el);
      this.redraw();
    }
    project(p) {
      const w = this.el.clientWidth, h = this.el.clientHeight;
      return { x: (mercX(p.lng) - this.cx) * this.scale + w / 2, y: (mercY(p.lat) - this.cy) * this.scale + h / 2 };
    }
    place(node, p) { const { x, y } = this.project(p); node.style.left = x + "px"; node.style.top = y + "px"; }
    pathD(points) { return points.map((p, i) => { const { x, y } = this.project(p); return (i ? "L" : "M") + x.toFixed(1) + " " + y.toFixed(1); }).join(" "); }
    redraw() { this.items.forEach(i => i.draw()); }
    zoomBy(f) { this.scale = Math.max(256 * 2 ** 8, Math.min(256 * 2 ** 18, this.scale * f)); this.redraw(); }

    addVehicle(v, { selected, onClick } = {}) {
      const el = vehicleElement(v);
      styleVehicle(el, v, selected);
      let cur = v;
      this.markers.append(el);
      const item = { draw: () => this.place(el, cur.pos) };
      this.items.add(item); item.draw();
      if (onClick) {
        el.addEventListener("click", e => { e.stopPropagation(); onClick(cur); });
        el.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onClick(cur); } });
      }
      return {
        update: (nv, o = {}) => { cur = nv; styleVehicle(el, nv, o.selected); el.style.zIndex = o.selected ? 10 : 1; item.draw(); },
        remove: () => { el.remove(); this.items.delete(item); }
      };
    }
    addPin(pos, { kind, label, title } = {}) {
      const el = pinElement(kind, label);
      if (title) el.title = title;
      el.style.zIndex = kind === "stop" ? 3 : 8;
      this.markers.append(el);
      const item = { draw: () => this.place(el, pos) };
      this.items.add(item); item.draw();
      return { remove: () => { el.remove(); this.items.delete(item); } };
    }
    addPath(points, { color = "#0B4F8A", weight = 5, dashed = false } = {}) {
      const path = document.createElementNS(SVGNS, "path");
      Object.entries({ fill: "none", stroke: color, "stroke-width": weight, "stroke-linecap": "round", "stroke-linejoin": "round", "stroke-opacity": 0.9 })
        .forEach(([k, val]) => path.setAttribute(k, val));
      if (dashed) path.setAttribute("stroke-dasharray", "2 9");
      this.svg.append(path);
      let pts = points;
      const item = { draw: () => path.setAttribute("d", this.pathD(pts)) };
      this.items.add(item); item.draw();
      return { setPath: p => { pts = p; item.draw(); }, remove: () => { path.remove(); this.items.delete(item); } };
    }
    fitBounds(points, padding = 60) {
      if (!points.length) return;
      const xs = points.map(p => mercX(p.lng)), ys = points.map(p => mercY(p.lat));
      const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
      const w = Math.max(100, this.el.clientWidth - padding * 2), h = Math.max(100, this.el.clientHeight - padding * 2);
      this.cx = (minX + maxX) / 2; this.cy = (minY + maxY) / 2;
      this.scale = Math.min(w / Math.max(maxX - minX, 1e-6), h / Math.max(maxY - minY, 1e-6), 256 * 2 ** 16);
      this.redraw();
    }
    panTo(p) { this.cx = mercX(p.lng); this.cy = mercY(p.lat); this.redraw(); }
    setTraffic() { /* not available on the schematic map */ }
  }

  function notice(el, html) {
    const n = document.createElement("div");
    n.className = "map-notice";
    n.innerHTML = html;
    el.parentElement.append(n);
  }

  async function create(el, opts) {
    try {
      const maps = await loadGoogle();
      window.gm_authFailure = () => notice(el, "Google Maps rejected the API key. Check the key and its allowed websites in Google Cloud Console.");
      return new GoogleMapView(el, maps, opts);
    } catch (e) {
      const why = e.message === "no-key"
        ? "Showing a schematic map. Add a Google Maps API key to <code>mockup/assets/config.js</code> to see Google Maps."
        : "Google Maps could not be loaded, so a schematic map is shown. Check your network and API key.";
      const view = new SchematicMapView(el, opts);
      notice(el, why);
      return view;
    }
  }

  window.VTMap = { create };
})();
