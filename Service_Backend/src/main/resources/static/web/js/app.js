/* Author: Guo Jiali */
document.addEventListener("DOMContentLoaded", () => {
  bindConfirmations();
  bindLoadingForms();
  bindSleepDuration();
  renderSparklines();
  renderBarCharts();
  scrollChatToBottom();
});

function bindConfirmations() {
  document.querySelectorAll("[data-confirm]").forEach((form) => {
    form.addEventListener("submit", (event) => {
      const message = form.getAttribute("data-confirm") || "Are you sure?";
      if (!window.confirm(message)) event.preventDefault();
    });
  });
}

function bindLoadingForms() {
  document.querySelectorAll("[data-loading-form]").forEach((form) => {
    form.addEventListener("submit", () => {
      const targetId = form.getAttribute("data-loading-target");
      if (targetId) {
        const target = document.getElementById(targetId);
        if (target) target.hidden = false;
      }
      form.querySelectorAll("button").forEach((button) => {
        button.disabled = true;
      });
    });
  });
}

function bindSleepDuration() {
  document.querySelectorAll("[data-sleep-form]").forEach((form) => {
    const sleepInput = form.querySelector("[data-sleep-time]");
    const wakeInput = form.querySelector("[data-wake-time]");
    const hoursInput = form.querySelector("[data-sleep-hours]");
    const label = form.querySelector("[data-duration-label]");
    const update = () => {
      const hours = calculateSleepHours(sleepInput.value, wakeInput.value);
      if (!Number.isFinite(hours)) {
        label.textContent = "Duration: --";
        return;
      }
      hoursInput.value = hours.toFixed(1);
      label.textContent = `Duration: ${hours.toFixed(1)} h`;
    };
    sleepInput.addEventListener("input", update);
    wakeInput.addEventListener("input", update);
    update();
  });
}

function calculateSleepHours(sleep, wake) {
  if (!sleep || !wake) return NaN;
  const [sleepH, sleepM] = sleep.split(":").map(Number);
  const [wakeH, wakeM] = wake.split(":").map(Number);
  if (![sleepH, sleepM, wakeH, wakeM].every(Number.isFinite)) return NaN;
  let minutes = wakeH * 60 + wakeM - (sleepH * 60 + sleepM);
  if (minutes < 0) minutes += 24 * 60;
  return minutes / 60;
}

function parseSeries(value) {
  if (!value) return [];
  return value.split(",").map((part) => Number(part.trim()) || 0);
}

function renderSparklines() {
  document.querySelectorAll(".sparkline").forEach((el) => {
    const values = parseSeries(el.dataset.series);
    const kind = el.dataset.kind || "sleep";
    const color = kind === "exercise" ? "#16a34a" : "#2563eb";
    const width = Math.max(el.clientWidth || 360, 240);
    const height = 52;
    const max = Math.max(...values, kind === "exercise" ? 60 : 10, 1);
    const step = values.length > 1 ? width / (values.length - 1) : width;
    const points = values.map((v, i) => {
      const x = i * step;
      const y = height - 6 - (v / max) * (height - 12);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(" ");
    el.innerHTML = `
      <svg viewBox="0 0 ${width} ${height}" role="img" aria-label="trend">
        <polyline points="${points}" fill="none" stroke="${color}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></polyline>
        ${values.map((v, i) => {
          const x = i * step;
          const y = height - 6 - (v / max) * (height - 12);
          return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="3" fill="${color}"></circle>`;
        }).join("")}
      </svg>`;
  });
}

function renderBarCharts() {
  document.querySelectorAll(".bar-chart").forEach((el) => {
    const values = parseSeries(el.dataset.series);
    const labels = (el.dataset.labels || "").split(",").filter(Boolean);
    const kind = el.dataset.kind || "sleep";
    const target = Number(el.dataset.target) || 0;
    const configuredMax = Number(el.dataset.max) || 0;
    const max = Math.max(configuredMax, target, ...values, 1);
    const colorStart = kind === "exercise" ? "#64e0b5" : "#45b5d6";
    const colorEnd = kind === "exercise" ? "#86efac" : "#93c5fd";
    const width = Math.max(el.clientWidth || 760, 320);
    const height = 280;
    const chartTop = 18;
    const chartBottom = 42;
    const chartHeight = height - chartTop - chartBottom;
    const gap = 12;
    const barWidth = Math.max(18, (width - gap * (values.length + 1)) / Math.max(values.length, 1));
    const targetY = chartTop + chartHeight - (target / max) * chartHeight;
    const gradientId = `barGradient${Math.random().toString(36).slice(2)}`;
    el.innerHTML = `
      <svg viewBox="0 0 ${width} ${height}" role="img" aria-label="weekly chart">
        <defs>
          <linearGradient id="${gradientId}" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stop-color="${colorStart}"></stop>
            <stop offset="100%" stop-color="${colorEnd}"></stop>
          </linearGradient>
        </defs>
        <line x1="8" x2="${width - 8}" y1="${targetY.toFixed(1)}" y2="${targetY.toFixed(1)}" stroke="#f59e0b" stroke-width="2" stroke-dasharray="6 6"></line>
        ${values.map((v, i) => {
          const h = Math.max(3, (v / max) * chartHeight);
          const x = gap + i * (barWidth + gap);
          const y = chartTop + chartHeight - h;
          const label = labels[i] || "";
          return `
            <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barWidth.toFixed(1)}" height="${h.toFixed(1)}" rx="8" fill="url(#${gradientId})"></rect>
            <text x="${(x + barWidth / 2).toFixed(1)}" y="${height - 18}" text-anchor="middle" fill="#64748b" font-size="12">${label}</text>
            <text x="${(x + barWidth / 2).toFixed(1)}" y="${Math.max(14, y - 6).toFixed(1)}" text-anchor="middle" fill="#1e293b" font-size="12" font-weight="700">${v || ""}</text>`;
        }).join("")}
      </svg>`;
  });
}

function scrollChatToBottom() {
  const chatWindow = document.getElementById("chatWindow");
  if (chatWindow) chatWindow.scrollTop = chatWindow.scrollHeight;
}
