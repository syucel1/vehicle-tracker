/* Shared page chrome and formatting helpers. */
(function () {
  "use strict";

  const NAV = [
    ["live-map.html", "Live map"],
    ["trips.html", "Trips"],
    ["fleet.html", "Vehicles & drivers"]
  ];

  function topbar(active) {
    const header = document.createElement("header");
    header.className = "topbar";
    header.innerHTML = `
      <a class="brand" href="live-map.html">
        <span class="brand-mark"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5 19 20l-7-4-7 4z"/></svg></span>
        Vehicle Tracker
      </a>
      <nav class="nav" aria-label="Main">
        ${NAV.map(([href, label]) => `<a href="${href}"${href === active ? ' aria-current="page"' : ""}>${label}</a>`).join("")}
      </nav>
      <div class="topbar-end">
        <span class="org">Northline Logistics</span>
        <a class="avatar" href="index.html" title="Serkan Yücel, Fleet manager. Select to sign out.">SY</a>
      </div>`;
    document.body.prepend(header);
  }

  const STATUS = {
    moving: "Moving",
    idle: "Idle, engine on",
    stopped: "Stopped, ignition off",
    offline: "Offline"
  };

  const esc = s => String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const plate = (p, lg) => `<span class="plate${lg ? " plate--lg" : ""}">${esc(p)}</span>`;
  const status = s => `<span class="status" data-status="${s}">${STATUS[s]}</span>`;
  const initials = name => name.split(/\s+/).map(w => w[0]).slice(0, 2).join("").toUpperCase();
  const time = d => d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  const date = iso => new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });

  function ago(d) {
    const s = Math.max(0, Math.round((Date.now() - d.getTime()) / 1000));
    if (s < 5) return "just now";
    if (s < 60) return `${s} s ago`;
    const m = Math.round(s / 60);
    if (m < 60) return `${m} min ago`;
    const h = Math.floor(m / 60);
    return `${h} h ${m % 60} min ago`;
  }
  function duration(min) {
    min = Math.round(min);
    if (min < 60) return `${min} min`;
    return `${Math.floor(min / 60)} h ${String(min % 60).padStart(2, "0")} min`;
  }
  const km = m => (m / 1000).toFixed(m < 10000 ? 1 : 0);

  let toastEl;
  function toast(msg) {
    if (!toastEl) {
      toastEl = document.createElement("div");
      toastEl.className = "toast"; toastEl.setAttribute("role", "status");
      document.body.append(toastEl);
    }
    toastEl.textContent = msg;
    toastEl.classList.add("is-on");
    clearTimeout(toastEl._t);
    toastEl._t = setTimeout(() => toastEl.classList.remove("is-on"), 2600);
  }

  /** Driver card markup, shared by the live map and trips pages. */
  function driverCard(d, extra = "") {
    return `
      <div class="driver">
        <span class="avatar" style="background:${d.color}">${initials(d.name)}</span>
        <div>
          <div class="who">${esc(d.name)}</div>
          <div class="role">${d.employeeId}, driver since ${date(d.joined)}</div>
        </div>
      </div>
      <dl class="facts">
        <dt>Phone</dt><dd><a href="tel:${d.phone.replace(/\s/g, "")}">${d.phone}</a></dd>
        <dt>Email</dt><dd><a href="mailto:${d.email}">${d.email}</a></dd>
        <dt>Licence</dt><dd class="mono">${d.licence}</dd>
        <dt>Categories</dt><dd>${d.licenceClass}, valid to ${date(d.licenceExpiry)}</dd>
        <dt>Rating</dt><dd>${d.rating.toFixed(1)} out of 5</dd>
        ${extra}
      </dl>`;
  }

  window.UI = { topbar, plate, status, STATUS, initials, time, date, ago, duration, km, toast, esc, driverCard };
})();
