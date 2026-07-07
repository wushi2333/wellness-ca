// Author: Xia Zihang
/* Live2D module for the web chat (port of Android Live2D subsystem).
   Replicates Android behavior: head sway, irregular blink, breath, watermark off,
   emotion/mode expressions, tap-for-expression, drag-follow, TTS lip-sync.
   Exposes window.YuiLive2D with init(canvas), setEmotion, setModeExpression, dispose. */
(function () {
  const TAP_THRESHOLD = 0.05; // normalized; squared distance < 0.0025 => tap
  const TAP_EXPRESSIONS = ["blackFace", "tears", "cry"];
  const EMOTION_MAP = {
    happy: { expr: "blush", eyeSmile: 1.5 },
    listening: { expr: "starEyes" },
    surprised: { expr: "leanForward", mouthOpen: 1.0 },
    focused: { expr: "notes" },
    confused: { expr: "dizzy" },
    thinking: { expr: "phone" },
  };
  const MODE_MAP = { chat: "phone", agent: "notes" };
  const BLINK_INTERVALS = [5, 7, 6, 5, 7, 6, 5, 8, 6, 7];

  let app = null;
  let model = null;
  let canvas = null;
  let startT = 0;
  let lastTapExpr = null;
  let idleResetTimer = null;
  let dragOverride = null; // {x,y} when dragging, else null
  let eyeSmileOverride = 0;
  let mouthOpenOverride = 0; // set externally by TTS via window.WEB_TTS_LEVEL handled below

  const YuiLive2D = {
    isReady: () => !!model,

    async init(canvasEl) {
      canvas = canvasEl;
      app = new PIXI.Application({
        view: canvas,
        backgroundAlpha: 0,
        antialias: true,
        autoStart: true,
        width: canvas.clientWidth || 148,
        height: canvas.clientHeight || 148,
      });
      console.log("[Live2D] PIXI ready, live2d=", !!PIXI.live2d, "canvas", canvas.width, canvas.height);
      model = await PIXI.live2d.Live2DModel.from("/web/live2d/YouXiaoMiao/悠小喵.web.model3.json");
      console.log("[Live2D] model loaded", !!model, "size", model && model.width, model && model.height);
      app.stage.addChild(model);

      // Scale to show the upper body (head + torso), like Android — not the full body.
      // Zoom in ~2x and anchor so the head sits in the upper-center of the stage.
      const fit = () => {
        const baseScale = Math.min(app.renderer.width / model.width, app.renderer.height / model.height);
        // Zoom in to crop to upper body: ~1.7x of fit-to-height shows head+torso.
        const scale = baseScale * 1.7;
        model.scale.set(scale);
        model.anchor.set(0.5, 0.0); // anchor at top-center
        model.x = app.renderer.width / 2;
        // Shift down slightly so the head isn't flush against the top.
        model.y = app.renderer.height * 0.08;
      };
      fit();
      window.addEventListener("resize", fit);

      // Hide watermark at init (Android starts the "watermark" expression).
      try { model.expression("watermark"); } catch (e) { console.warn("[Live2D] watermark expr", e); }

      startT = performance.now();
      app.ticker.add(onTick);

      // Pointer interactions: drag-follow + tap expression.
      let downX = 0, downY = 0, downTime = 0, dragging = false;
      const norm = (e) => {
        const r = canvas.getBoundingClientRect();
        return { x: (e.clientX - r.left) / r.width * 2 - 1, y: (e.clientY - r.top) / r.height * 2 - 1 };
      };
      canvas.addEventListener("pointerdown", (e) => {
        const p = norm(e);
        downX = p.x; downY = p.y; downTime = performance.now();
        dragging = true;
        resetIdleTimer(); // Android resets the 5s timer on every touch event
      });
      canvas.addEventListener("pointermove", (e) => {
        if (!dragging) return;
        const p = norm(e);
        dragOverride = { x: p.x, y: -p.y }; // Y flipped to match Android
        resetIdleTimer();
      });
      const endDrag = (e) => {
        if (!dragging) return;
        dragging = false;
        const p = norm(e);
        const dx = p.x - downX, dy = p.y - downY;
        const dist2 = dx * dx + dy * dy;
        dragOverride = null; // release => head returns to center (Android resets to 0,0)
        resetIdleTimer();
        if (dist2 < TAP_THRESHOLD * TAP_THRESHOLD) onTap();
      };
      canvas.addEventListener("pointerup", endDrag);
      canvas.addEventListener("pointerleave", endDrag);
      canvas.addEventListener("pointercancel", endDrag);
    },

    setEmotion(emotion) {
      const m = EMOTION_MAP[emotion];
      eyeSmileOverride = 0; mouthOpenOverride = 0;
      if (!m) return;
      eyeSmileOverride = m.eyeSmile || 0;
      mouthOpenOverride = m.mouthOpen || 0;
      if (m.expr) { try { model.expression(m.expr); } catch (_) {} lastTapExpr = m.expr; }
      resetIdleTimer();
    },

    setModeExpression(mode) {
      const name = MODE_MAP[mode];
      if (name) { try { model.expression(name); } catch (_) {} }
    },

    dispose() {
      try { app && app.destroy(true); } catch (_) {}
      app = null; model = null; canvas = null;
    },
  };

  function onTap() {
    const candidates = TAP_EXPRESSIONS.filter((n) => n !== lastTapExpr);
    const name = (candidates.length ? candidates : TAP_EXPRESSIONS)[Math.floor(Math.random() * (candidates.length || TAP_EXPRESSIONS.length))];
    lastTapExpr = name;
    try { model.expression(name); } catch (_) {}
    resetIdleTimer();
  }

  function resetIdleTimer() {
    if (idleResetTimer) clearTimeout(idleResetTimer);
    idleResetTimer = setTimeout(() => {
      // 5s of no interaction => clear expressions (Android stopAllExpressions)
      try { model.internalModel.motionManager.expressionManager.stopAllMotions(); } catch (_) {}
      lastTapExpr = null;
      // Also clear emotion overrides so happy squint / surprised mouth don't linger.
      eyeSmileOverride = 0;
      mouthOpenOverride = 0;
    }, 5000);
  }

  // Per-frame parameter drive — replicates Android LAppMinimumModel.update().
  function onTick() {
    if (!model) return;
    const t = (performance.now() - startT) / 1000;
    const core = model.internalModel.coreModel;

    // 1. Head sway (idle). Overridden by drag when active.
    if (dragOverride) {
      core.setParameterValueById("ParamAngleX", dragOverride.x * 30);
      core.setParameterValueById("ParamAngleY", dragOverride.y * 30);
      core.setParameterValueById("ParamAngleZ", -dragOverride.x * 30);
      core.setParameterValueById("ParamBodyAngleX", dragOverride.x * 10);
      core.setParameterValueById("ParamEyeBallX", dragOverride.x);
      core.setParameterValueById("ParamEyeBallY", Math.max(0, dragOverride.y));
    } else {
      core.setParameterValueById("ParamAngleX", Math.sin(t * 0.7) * 5);
      core.setParameterValueById("ParamAngleY", Math.sin(t * 1.1) * 1.25);
      core.setParameterValueById("ParamAngleZ", Math.cos(t * 0.9) * 3.5);
      core.setParameterValueById("ParamBodyAngleX", Math.sin(t * 0.7) * 3);
    }

    // 2. Breath (3s cycle, peak 0.5).
    core.setParameterValueById("ParamBreath", (Math.sin(t * 2 * Math.PI / 3) * 0.5 + 0.5));

    // 3. Irregular blink — Android algorithm.
    const idx = Math.floor((t / 50) % BLINK_INTERVALS.length);
    const cyclePos = (t % BLINK_INTERVALS[idx]) / BLINK_INTERVALS[idx];
    let blink = cyclePos > 0.97 ? 0 : 1;
    if (eyeSmileOverride > 0) blink = 0.6; // happy => crescent eyes
    core.setParameterValueById("ParamEyeLOpen", blink);
    core.setParameterValueById("ParamEyeROpen", blink);

    // 4. Emotion overrides (happy squint).
    core.setParameterValueById("ParamEyeSquint", eyeSmileOverride, 1.0);
    core.setParameterValueById("ParamMouthForm", eyeSmileOverride, 1.0);

    // 5. Mouth / lip-sync. TTS level takes priority; else emotion mouthOpenOverride.
    const ttsLevel = window.WEB_TTS_LEVEL || 0;
    const mouth = ttsLevel > 0 ? ttsLevel : mouthOpenOverride;
    core.setParameterValueById("ParamMouthOpenY", mouth, 1.0);
    core.setParameterValueById("ParamJawOpen", mouth, 1.0);

    // 6. Watermark off every frame (Android forces Param85=1.0).
    try { core.setParameterValueById("Param85", 1.0, 1.0); } catch (_) {}
  }

  window.YuiLive2D = YuiLive2D;
})();
