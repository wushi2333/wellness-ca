/* Author: Guo Jiali, Xia Zihang */
/* Web overhaul — Phase 0 (Turbo + chrome) & #1 (ambient glow, ripples, page transitions) */

/* ---- Load Turbo Drive once (UMD global). Injected from <head>, persists across navigations. ---- */
(function loadTurbo() {
  if (window.Turbo) return;
  const s = document.createElement("script");
  s.src = "/web/js/vendor/turbo.js";
  s.async = false;
  document.head.appendChild(s);
})();

/* ---- Inject the overhaul stylesheet once (keeps the 1.1k-line style.css untouched). ---- */
(function loadOverhaulCss() {
  if (document.querySelector('link[data-overhaul]')) return;
  const l = document.createElement("link");
  l.rel = "stylesheet";
  l.setAttribute("data-overhaul", "1");
  l.href = "/web/css/overhaul.css";
  document.head.appendChild(l);
})();

/* ---- Page-transition: lightweight CSS fade only. Turbo swaps <body> instantly (no white flash).
   Skip the very first render (initial page load already visible) so <main> only animates on
   subsequent navigations — avoids the "double bounce" on first entry. ---- */
let firstRender = true;
document.addEventListener("turbo:visit", () => document.body.classList.add("turbo-loading"));
document.addEventListener("turbo:render", () => {
  document.body.classList.remove("turbo-loading");
  if (firstRender) { firstRender = false; return; }
  const main = document.querySelector("main");
  if (main && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    main.classList.remove("vt-enter");
    void main.offsetWidth; /* reflow to restart animation */
    main.classList.add("vt-enter");
    main.addEventListener("animationend", () => main.classList.remove("vt-enter"), { once: true });
  }
});

/* ---- Ambient mouse glow + click ripples. Mounted under <html> so Turbo (which swaps <body>) never removes it.
   Throttled via rAF so the radial-gradient repaints at most once per frame — light on the visitor's device. ---- */
const AmbientLayer = (() => {
  let layer = null;
  let pendingX = 0, pendingY = 0, scheduled = false;
  function ensure() {
    if (layer && document.documentElement.contains(layer)) return layer;
    layer = document.getElementById("ambient-layer");
    if (!layer) {
      layer = document.createElement("div");
      layer.id = "ambient-layer";
      layer.setAttribute("aria-hidden", "true");
      document.documentElement.appendChild(layer);
    }
    return layer;
  }
  function onMove(e) {
    pendingX = e.clientX;
    pendingY = e.clientY;
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      const el = ensure();
      el.style.setProperty("--mx", pendingX + "px");
      el.style.setProperty("--my", pendingY + "px");
      el.classList.add("active");
      scheduled = false;
    });
  }
  function onDown(e) {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const el = ensure();
    const r = document.createElement("span");
    r.className = "ripple";
    r.style.left = e.clientX + "px";
    r.style.top = e.clientY + "px";
    el.appendChild(r);
    r.addEventListener("animationend", () => r.remove(), { once: true });
  }
  window.addEventListener("pointermove", onMove, { passive: true });
  window.addEventListener("pointerdown", onDown, { passive: true });
  return { ensure };
})();

/* ---- Floating Live2D companion (#8): draggable circle, head tracks mouse,
   click opens an Agent chat popup. Persistent across Turbo navigations (lives under <html>). ---- */
let companionBound = false;
let companionSessionId = null;
function ensureCompanion() {
  if (document.getElementById("companion")) {
    // Already built; just refresh visibility for the current page.
    syncCompanionVisibility();
    return;
  }
  const c = document.createElement("div");
  c.id = "companion";
  c.innerHTML = `
    <div id="companionBubble" class="companion-bubble" hidden>
      <div class="companion-head">
        <strong>Yui</strong>
        <button type="button" id="companionClose" aria-label="close">×</button>
      </div>
      <div class="companion-messages" id="companionMessages"></div>
      <form id="companionForm" class="companion-form" data-turbo="false">
        <input type="text" id="companionInput" placeholder="Ask Yui..." autocomplete="off">
        <button type="submit">Send</button>
      </form>
    </div>
    <div id="companionOrb" class="companion-orb">
      <img id="companionFace" src="/web/live2d/YouXiaoMiao/icon.jpg" alt="Yui">
    </div>`;
  document.documentElement.appendChild(c);

  // Restore saved position (left/right edge + y).
  try {
    const side = localStorage.getItem("companionSide") || "right";
    const y = localStorage.getItem("companionY");
    if (y) { c.style.top = y + "px"; c.style.bottom = "auto"; }
    c.classList.toggle("left", side === "left");
  } catch (_) {}

  bindCompanion();
  syncCompanionVisibility();
}

function syncCompanionVisibility() {
  const orb = document.getElementById("companionOrb");
  const bubble = document.getElementById("companionBubble");
  if (!orb) return;
  // Hide on chat pages (has its own Live2D) and auth pages (login/register — not logged in yet).
  const onChat = document.body.classList.contains("chat-body");
  const onAuth = document.body.classList.contains("auth-page");
  const hide = onChat || onAuth;
  orb.style.display = hide ? "none" : "";
  if (hide && bubble) bubble.hidden = true;
}

function bindCompanion() {
  if (companionBound) return;
  companionBound = true;
  const orb = document.getElementById("companionOrb");
  const bubble = document.getElementById("companionBubble");
  const closeBtn = document.getElementById("companionClose");
  const form = document.getElementById("companionForm");
  const input = document.getElementById("companionInput");
  const messages = document.getElementById("companionMessages");
  if (!orb) return;

  // --- Dragging vs click detection ---
  let dragging = false, moved = false, startX = 0, startY = 0, origY = 0;
  orb.addEventListener("pointerdown", (e) => {
    dragging = true; moved = false;
    startX = e.clientX; startY = e.clientY;
    const c = document.getElementById("companion");
    origY = parseInt(c.style.top || (window.innerHeight - 108));
    orb.setPointerCapture(e.pointerId);
  });
  orb.addEventListener("pointermove", (e) => {
    if (!dragging) return;
    const dx = e.clientX - startX, dy = e.clientY - startY;
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) moved = true;
    const c = document.getElementById("companion");
    let newY = origY + dy;
    newY = Math.max(80, Math.min(window.innerHeight - 100, newY));
    c.style.top = newY + "px";
    c.style.bottom = "auto";
    const side = e.clientX < window.innerWidth / 2 ? "left" : "right";
    c.classList.toggle("left", side === "left");
  });
  orb.addEventListener("pointerup", (e) => {
    dragging = false;
    try { orb.releasePointerCapture(e.pointerId); } catch (_) {}
    if (moved) {
      const c = document.getElementById("companion");
      try {
        localStorage.setItem("companionSide", c.classList.contains("left") ? "left" : "right");
        localStorage.setItem("companionY", parseInt(c.style.top || 0));
      } catch (_) {}
    } else {
      toggleBubble();
    }
  });

  function toggleBubble() {
    bubble.hidden = !bubble.hidden;
    if (!bubble.hidden) {
      const c = document.getElementById("companion");
      bubble.classList.toggle("left", c.classList.contains("left"));
      if (messages.children.length === 0) {
        appendCompanionMessage("ai", (window.WEB_I18N && window.WEB_I18N.welcome) || "Hello! I'm Yui. How can I help?");
      }
      input.focus();
    }
  }

  closeBtn.addEventListener("click", () => { bubble.hidden = true; });

  // --- Agent chat: send via /web/chat/send in agent mode ---
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    appendCompanionMessage("user", text);
    input.value = "";
    const typing = appendCompanionMessage("ai", "");
    typing.classList.add("typing");
    typing.innerHTML = '<div class="typing-dots"><span></span><span></span><span></span></div>';
    const body = new FormData();
    body.set("message", text);
    body.set("mode", "agent");
    body.set("sessionId", companionSessionId || "");
    fetch("/web/chat/send", {
      method: "POST", body, credentials: "same-origin",
      headers: { Accept: "application/json" },
    }).then((r) => r.json()).then((j) => {
      typing.classList.remove("typing");
      typing.innerHTML = "";
      if (j.error) { typing.textContent = j.error; return; }
      if (j.sessionId != null) companionSessionId = j.sessionId;
      typing.textContent = j.reply || "";
      if (j.intentTarget) navigateCompanion(j.intentTarget);
    }).catch(() => { typing.textContent = "Network error"; });
  });
}

function appendCompanionMessage(role, text) {
  const messages = document.getElementById("companionMessages");
  if (!messages) return null;
  const div = document.createElement("div");
  div.className = "companion-msg " + role;
  div.textContent = text;
  messages.appendChild(div);
  messages.scrollTop = messages.scrollHeight;
  return div;
}

// Map Android navigate targets to web URLs (shared intent contract across platforms).
function navigateCompanion(target) {
  const map = {
    sleep_detail: "/web/sleep-detail",
    exercise_detail: "/web/exercise-detail",
    wellness_entry: "/web/records/new",
    wellness_insights: "/web/insights",
    dashboard: "/web/dashboard",
  };
  const url = map[target] || "/web/dashboard";
  const bubble = document.getElementById("companionBubble");
  if (bubble) bubble.hidden = true;
  if (window.Turbo) Turbo.visit(url);
  else window.location.href = url;
}

// Head-tracking: the companion face image tilts toward the global mouse position.
window.addEventListener("pointermove", (e) => {
  const orb = document.getElementById("companionOrb");
  const face = document.getElementById("companionFace");
  if (!orb || !face || orb.style.display === "none") return;
  const r = orb.getBoundingClientRect();
  const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
  const nx = Math.max(-1, Math.min(1, (e.clientX - cx) / (window.innerWidth / 3)));
  const ny = Math.max(-1, Math.min(1, (e.clientY - cy) / (window.innerHeight / 3)));
  face.style.transform = `translate(${nx * 6}px, ${ny * 6}px) rotate(${nx * 4}deg)`;
}, { passive: true });

/* ---- Per-page init: runs on first load and after every Turbo navigation. ---- */
function initPage() {
  AmbientLayer.ensure();
  ensureCompanion();
  bindConfirmations();
  bindLoadingForms();
  bindSleepDuration();
  bindChatSend();
  bindChatMic();
  bindTtsToggle();
  bindModeToggle();
  bindChatLive2D();
  bindRailToggle();
  renderSparklines();
  renderBarCharts();
  scrollChatToBottom();
}

document.addEventListener("DOMContentLoaded", initPage);
document.addEventListener("turbo:render", initPage);

/* ===================================================================== */
/* Chat: mode toggle via fetch (no page reload => Live2D canvas survives) */
/* ===================================================================== */

function bindModeToggle() {
  const form = document.querySelector("[data-mode-toggle]");
  if (!form) return;
  const sendForm = document.querySelector("[data-chat-send]");
  const modeInput = sendForm && sendForm.querySelector('input[name="mode"]');
  form.querySelectorAll('button[name="mode"]').forEach((btn) => {
    btn.addEventListener("click", () => {
      const mode = btn.value;
      // Update active styling.
      form.querySelectorAll('button').forEach((b) => b.classList.toggle("active", b === btn));
      // Sync the hidden mode used by the send form.
      if (modeInput) modeInput.value = mode;
      // Toggle the agent styling on the Live2D stage.
      const stage = document.getElementById("yuiStage");
      if (stage) stage.classList.toggle("agent", mode === "agent");
      // Drive the Live2D mode expression.
      if (window.YuiLive2D && window.YuiLive2D.isReady()) window.YuiLive2D.setModeExpression(mode);
      // Persist on the server (fire-and-forget).
      fetch(form.action, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "mode=" + encodeURIComponent(mode),
        credentials: "same-origin",
      }).catch(() => {});
    });
  });
}

/* ===================================================================== */
/* Chat: Live2D character (#6)                                            */
/* ===================================================================== */

let live2dInitStarted = false;
function bindChatLive2D() {
  const canvas = document.getElementById("yuiCanvas");
  if (!canvas) return;
  // Turbo replaces <body> on navigation, so the canvas element is new each time.
  // If this canvas isn't the one we initialized, dispose the old instance and re-init.
  if (live2dInitStarted && !canvas.dataset.l2dReady) {
    try { window.YuiLive2D && window.YuiLive2D.dispose(); } catch (_) {}
    live2dInitStarted = false;
  }
  if (live2dInitStarted) return;

  // Ensure the Live2D module + vendor libs are loaded (Turbo navigations may skip
  // the chat page's <head> scripts, so load them on demand here).
  const ensureScript = (src) => new Promise((resolve) => {
    if (document.querySelector('script[data-l2d-src="' + src + '"]')) return resolve();
    const s = document.createElement("script");
    s.src = src; s.setAttribute("data-l2d-src", src);
    s.onload = () => resolve();
    s.onerror = () => { console.error("[Live2D] failed to load", src); resolve(); };
    document.head.appendChild(s);
  });

  (async () => {
    await ensureScript("/web/js/vendor/live2dcubismcore.min.js");
    await ensureScript("/web/js/vendor/pixi.min.js");
    await ensureScript("/web/js/vendor/pixi-live2d-display.min.js");
    await ensureScript("/web/js/live2d.js");
    if (!window.YuiLive2D) { console.warn("[Live2D] live2d.js still not loaded"); return; }
    if (!window.PIXI || !window.PIXI.live2d || !window.PIXI.live2d.Live2DModel) {
      console.log("[Live2D] waiting for PIXI.live2d...");
      setTimeout(() => requestAnimationFrame(initLive2DNow), 150);
      return;
    }
    initLive2DNow();
  })();

  function initLive2DNow() {
    if (live2dInitStarted) return;
    if (!window.YuiLive2D || !window.PIXI || !window.PIXI.live2d || !window.PIXI.live2d.Live2DModel) {
      setTimeout(() => requestAnimationFrame(initLive2DNow), 150);
      return;
    }
    live2dInitStarted = true;
    const loading = document.getElementById("yuiLoading");
    const r = canvas.getBoundingClientRect();
    canvas.width = r.width; canvas.height = r.height;
    canvas.dataset.l2dReady = "1";
    window.YuiLive2D.init(canvas).then(() => {
      if (loading) loading.style.display = "none";
      const form = document.querySelector("[data-chat-send]");
      const modeInput = form && form.querySelector('input[name="mode"]');
      if (modeInput && modeInput.value) window.YuiLive2D.setModeExpression(modeInput.value);
    }).catch((e) => {
      console.error("[Live2D] init failed", e);
      if (loading) loading.textContent = "Live2D unavailable";
    });
  }
}

/* ===================================================================== */
/* Chat: ASR (hold-to-record) + TTS (auto-play reply) (#3)                */
/* ===================================================================== */

// TTS toggle state (persisted). On by default. Live2D lip-sync (#6) hooks into onTtsLevel.
window.WEB_TTS_ENABLED = (() => {
  try { return localStorage.getItem("chatTts") !== "off"; } catch (_) { return true; }
})();
let chatTtsAudio = null; // current <audio> playing TTS
let chatTtsRaf = null;   // rAF id for lip-sync hook

function playTts(text, emotion) {
  stopTts();
  const form = document.querySelector("[data-chat-send]");
  fetch((form ? form.dataset.ttsUrl : "/web/chat/tts") || "/web/chat/tts", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "text=" + encodeURIComponent(text) + "&emotion=" + encodeURIComponent(emotion || ""),
    credentials: "same-origin",
  })
    .then((r) => (r.ok ? r.blob() : Promise.reject()))
    .then((blob) => {
      const url = URL.createObjectURL(blob);
      const audio = new Audio(url);
      chatTtsAudio = audio;
      audio.play().catch(() => {});
      // Lip-sync hook: drive a 0..1 mouth level while playing (Live2D #6 consumes window.WEB_TTS_LEVEL).
      const startT = performance.now();
      const tick = () => {
        if (!chatTtsAudio || chatTtsAudio.ended || chatTtsAudio.paused) {
          window.WEB_TTS_LEVEL = 0;
          chatTtsRaf = null;
          return;
        }
        // Android formula: v = (sin(pos*PI*5)*0.5+0.5)*0.7  (2.5Hz, 0..0.7)
        const pos = (performance.now() - startT) / 1000;
        window.WEB_TTS_LEVEL = (Math.sin(pos * Math.PI * 5.0) * 0.5 + 0.5) * 0.7;
        chatTtsRaf = requestAnimationFrame(tick);
      };
      tick();
      audio.addEventListener("ended", () => {
        window.WEB_TTS_LEVEL = 0;
        URL.revokeObjectURL(url);
        chatTtsAudio = null;
      });
    })
    .catch(() => {});
}

function stopTts() {
  if (chatTtsRaf) cancelAnimationFrame(chatTtsRaf);
  chatTtsRaf = null;
  if (chatTtsAudio) {
    try { chatTtsAudio.pause(); } catch (_) {}
    chatTtsAudio = null;
  }
  window.WEB_TTS_LEVEL = 0;
}

function bindChatMic() {
  const mic = document.getElementById("chatMic");
  const form = document.querySelector("[data-chat-send]");
  if (!mic || !form) return;
  const input = form.querySelector('input[name="message"]');
  const i18n = window.WEB_I18N || {};
  let mediaStream = null;
  let audioCtx = null;
  let workletNode = null;
  let chunks = [];
  let recording = false;

  async function startRec() {
    if (recording) return;
    if (!window.isSecureContext || !navigator.mediaDevices) {
      alert(i18n.mic || "Hold to speak");
      return;
    }
    try {
      mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true },
      });
    } catch (e) {
      alert(i18n.mic || "Hold to speak");
      return;
    }
    audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
    await audioCtx.audioWorklet.addModule("/web/js/pcm-capture-processor.js");
    const src = audioCtx.createMediaStreamSource(mediaStream);
    workletNode = new AudioWorkletNode(audioCtx, "pcm-capture-processor");
    workletNode.port.onmessage = (ev) => chunks.push(new Uint8Array(ev.data));
    src.connect(workletNode);
    // Don't connect to destination (no playback / feedback).
    chunks = [];
    recording = true;
    mic.classList.add("recording");
    mic.title = i18n.recording || "Recording...";
  }

  function stopRec() {
    if (!recording) return;
    recording = false;
    mic.classList.remove("recording");
    mic.title = i18n.mic || "Hold to speak";
    try { mediaStream && mediaStream.getTracks().forEach((t) => t.stop()); } catch (_) {}
    try { workletNode && workletNode.disconnect(); } catch (_) {}
    try { audioCtx && audioCtx.close(); } catch (_) {}
    mediaStream = null; workletNode = null; audioCtx = null;
    // Assemble PCM
    const total = chunks.reduce((a, c) => a + c.length, 0);
    if (total < 800) {
      chunks = [];
      flashPlaceholder(input, i18n.tooShort || "Too short");
      return;
    }
    const pcm = new Uint8Array(total);
    let off = 0;
    for (const c of chunks) { pcm.set(c, off); off += c.length; }
    chunks = [];
    transcribe(pcm, input, i18n);
  }

  // Press-and-hold on desktop, touch-start/end on mobile.
  mic.addEventListener("pointerdown", (e) => { e.preventDefault(); startRec(); });
  mic.addEventListener("pointerup", () => stopRec());
  mic.addEventListener("pointerleave", () => { if (recording) stopRec(); });
  mic.addEventListener("pointercancel", () => stopRec());
}

function transcribe(pcm, input, i18n) {
  const prevPlaceholder = input.placeholder;
  input.placeholder = i18n.transcribing || "Transcribing...";
  // base64 the raw PCM bytes
  let bin = "";
  for (let i = 0; i < pcm.length; i++) bin += String.fromCharCode(pcm[i]);
  const b64 = btoa(bin);
  fetch("/web/chat/asr", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "audio=" + encodeURIComponent(b64),
    credentials: "same-origin",
  })
    .then((r) => r.json())
    .then((j) => {
      input.placeholder = prevPlaceholder;
      if (j.text && j.text.trim()) {
        input.value = j.text.trim();
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);
      } else if (j.error) {
        flashPlaceholder(input, i18n.asrFailed || "Didn't catch that");
      } else {
        flashPlaceholder(input, i18n.asrFailed || "Didn't catch that");
      }
    })
    .catch(() => {
      input.placeholder = prevPlaceholder;
      flashPlaceholder(input, i18n.asrFailed || "Didn't catch that");
    });
}

function flashPlaceholder(input, msg) {
  const prev = input.placeholder;
  input.placeholder = msg;
  setTimeout(() => { if (input.placeholder === msg) input.placeholder = prev; }, 1800);
}

function bindTtsToggle() {
  const btn = document.getElementById("ttsToggle");
  if (!btn) return;
  const i18n = window.WEB_I18N || {};
  // Sync button state with WEB_TTS_ENABLED (set at load from localStorage).
  const sync = () => {
    const on = !!window.WEB_TTS_ENABLED;
    btn.setAttribute("aria-pressed", on ? "true" : "false");
    btn.title = on ? (i18n.ttsOn || "Voice on") : (i18n.ttsOff || "Voice off");
  };
  sync();
  btn.addEventListener("click", () => {
    window.WEB_TTS_ENABLED = !window.WEB_TTS_ENABLED;
    try { localStorage.setItem("chatTts", window.WEB_TTS_ENABLED ? "on" : "off"); } catch (_) {}
    sync();
    // If turning off while audio is playing, stop it immediately.
    if (!window.WEB_TTS_ENABLED) stopTts();
  });
}

/* ===================================================================== */
/* Chat: instant send + loading spinner (#10)                             */
/* ===================================================================== */

function bindChatSend() {
  const form = document.querySelector("[data-chat-send]");
  if (!form) return;
  const input = form.querySelector('input[name="message"]');
  const sessionIdInput = form.querySelector('input[name="sessionId"]');
  const sendBtn = form.querySelector("button.send-button");
  const chatWindow = document.getElementById("chatWindow");
  const notices = document.getElementById("chatNotices");
  const i18n = window.WEB_I18N || {};

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const text = (input.value || "").trim();
    if (!text || sendBtn.disabled) return;

    // Clear the empty-chat placeholder if present.
    const empty = chatWindow.querySelector(".empty-chat");
    if (empty) empty.remove();

    // Build the request body BEFORE clearing the input — FormData reads live input values,
    // so clearing first would send an empty message.
    const body = new FormData(form);
    body.set("message", text);

    // 1) Immediately show the user's message.
    appendBubble(chatWindow, "user", text);
    input.value = "";
    input.focus();
    scrollChatToBottom();

    // 2) Show a typing bubble with a spinner.
    const typing = appendTyping(chatWindow);
    scrollChatToBottom();
    sendBtn.disabled = true;

    fetch(form.action, {
      method: "POST",
      body,
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    })
      .then((r) => r.json())
      .then((j) => {
        typing.remove();
        sendBtn.disabled = false;
        if (j.error) {
          appendBubble(chatWindow, "ai", j.error === "unavailable"
            ? (i18n.unavailable || "Yui is temporarily unavailable. Please try again.")
            : (i18n.error || "Something went wrong."), "confused");
          scrollChatToBottom();
          return;
        }
        if (j.sessionId != null && sessionIdInput) sessionIdInput.value = j.sessionId;
        appendBubble(chatWindow, "ai", j.reply || "", j.emotion || "", j.tools || []);
        scrollChatToBottom();
        if (j.notice) showNotice(notices, j.notice, "success");
        if (j.intentTarget) showNotice(notices, (i18n.intentOpen || "Yui suggested opening:") + " " + j.intentTarget, "info");
        // Drive the Live2D emotion + TTS lip-sync.
        if (j.emotion && window.YuiLive2D && window.YuiLive2D.isReady()) window.YuiLive2D.setEmotion(j.emotion);
        if (j.reply && window.WEB_TTS_ENABLED && window.isSecureContext) {
          playTts(j.reply, j.emotion || "");
        }
      })
      .catch(() => {
        typing.remove();
        sendBtn.disabled = false;
        appendBubble(chatWindow, "ai", i18n.networkError || "Network error. Please try again.", "confused");
        scrollChatToBottom();
      });
  });
}

function appendBubble(container, role, text, emotion, tools) {
  const article = document.createElement("article");
  article.className = "chat-bubble " + (role === "user" ? "user" : "ai");
  const p = document.createElement("p");
  p.textContent = text;
  article.appendChild(p);
  if (role !== "user" && (emotion || (tools && tools.length))) {
    const meta = document.createElement("div");
    meta.className = "message-meta";
    if (emotion) {
      const s = document.createElement("span");
      s.textContent = emotion;
      meta.appendChild(s);
    }
    if (tools && tools.length) {
      const s = document.createElement("span");
      s.textContent = "Tools: " + tools.join(", ");
      meta.appendChild(s);
    }
    article.appendChild(meta);
  }
  container.appendChild(article);
  return article;
}

function appendTyping(container) {
  const article = document.createElement("article");
  article.className = "chat-bubble ai typing";
  article.innerHTML = '<div class="typing-dots"><span></span><span></span><span></span></div>';
  container.appendChild(article);
  return article;
}

function showNotice(container, text, kind) {
  if (!container) return;
  const div = document.createElement("div");
  div.className = "alert " + (kind === "info" ? "info" : "success");
  div.textContent = text;
  container.appendChild(div);
}

/* ===================================================================== */
/* Chat: collapsible history rail (#4)                                    */
/* ===================================================================== */

function bindRailToggle() {
  const rail = document.getElementById("sessionRail");
  const collapseBtn = document.getElementById("railCollapse");
  const reopenBtn = document.getElementById("railReopen");
  const shell = document.querySelector(".chat-shell");
  if (!rail || !collapseBtn) return;

  const apply = (collapsed) => {
    rail.classList.toggle("collapsed", collapsed);
    if (reopenBtn) reopenBtn.hidden = !collapsed;
    // Shrink the grid column so the main chat area expands to fill the gap (desktop only).
    if (shell && window.innerWidth > 720) {
      shell.style.setProperty("--rail-w", collapsed ? "0px" : "300px");
    }
    try { localStorage.setItem("chatRailCollapsed", collapsed ? "1" : "0"); } catch (_) {}
  };

  // Restore preference (only on desktop layout).
  try {
    if (localStorage.getItem("chatRailCollapsed") === "1" && window.innerWidth > 720) apply(true);
  } catch (_) {}

  collapseBtn.addEventListener("click", () => apply(true));
  if (reopenBtn) reopenBtn.addEventListener("click", () => apply(false));
}

/* ===================================================================== */
/* Existing page helpers (unchanged)                                      */
/* ===================================================================== */

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
