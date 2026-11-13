// Studio 64 para Android: controles tactiles. Solo se activa dentro de la app (existe window.AndroidBridge).
//  1) Pantalla completa: joystick + botones de SM64 (solo mientras el juego esta a pantalla completa).
//  2) Biblioteca: arrastrar un objeto con el dedo hasta el nivel y soltarlo para colocarlo.
(function () {
  if (!window.AndroidBridge) return;
  document.documentElement.classList.add('android');

  const $ = (s) => document.querySelector(s);
  const host = () => document.getElementById('game-frame') || document.getElementById('stage');
  const canvas = () => document.getElementById('canvas');
  const fsEl = () => document.fullscreenElement || document.webkitFullscreenElement || null;

  // ---------------------------------------------------------------- teclas sinteticas
  const KEYS = {
    KeyW: { key: 'w', kc: 87 }, KeyA: { key: 'a', kc: 65 }, KeyS: { key: 's', kc: 83 }, KeyD: { key: 'd', kc: 68 },
    Space: { key: ' ', kc: 32 }, KeyJ: { key: 'j', kc: 74 }, ShiftLeft: { key: 'Shift', kc: 16 },
    ArrowLeft: { key: 'ArrowLeft', kc: 37 }, ArrowRight: { key: 'ArrowRight', kc: 39 },
    ArrowUp: { key: 'ArrowUp', kc: 38 }, ArrowDown: { key: 'ArrowDown', kc: 40 },
    Enter: { key: 'Enter', kc: 13 },
  };
  const held = new Set();
  function setKey(code, down) {
    if (down === held.has(code)) return;
    if (down) held.add(code); else held.delete(code);
    const k = KEYS[code];
    const c = canvas();
    if (!c) return;
    if (down && document.activeElement !== c) c.focus();
    c.dispatchEvent(new KeyboardEvent(down ? 'keydown' : 'keyup', {
      key: k.key, code, keyCode: k.kc, which: k.kc, shiftKey: held.has('ShiftLeft'), bubbles: true, cancelable: true,
    }));
  }
  function releaseAll() { for (const c of [...held]) setKey(c, false); }

  // ---------------------------------------------------------------- controles en pantalla completa
  let root = null;

  function build() {
    const h = host();
    if (!h || root) return;
    root = document.createElement('div');
    root.id = 'tc';
    root.hidden = true;
    root.innerHTML = `
      <div class="tc-stick"><div class="tc-base"><div class="tc-knob"></div></div></div>
      <button class="tc-btn tc-z" data-k="ShiftLeft">Z</button>
      <button class="tc-btn tc-a" data-k="Space">A</button>
      <button class="tc-btn tc-b" data-k="KeyJ">B</button>
      <button class="tc-btn tc-c tc-cl" data-k="ArrowLeft">◀</button>
      <button class="tc-btn tc-c tc-cr" data-k="ArrowRight">▶</button>
      <button class="tc-btn tc-start" data-k="Enter">START</button>`;
    h.appendChild(root);

    // botones: pulsar = keydown, soltar = keyup (varios dedos a la vez)
    for (const b of root.querySelectorAll('.tc-btn')) {
      const code = b.dataset.k;
      b.addEventListener('pointerdown', (e) => {
        e.preventDefault(); e.stopPropagation();
        try { b.setPointerCapture(e.pointerId); } catch { /* */ }
        b.classList.add('on');
        setKey(code, true);
      });
      const up = (e) => { e.preventDefault(); e.stopPropagation(); b.classList.remove('on'); setKey(code, false); };
      b.addEventListener('pointerup', up);
      b.addEventListener('pointercancel', up);
      b.addEventListener('contextmenu', (e) => e.preventDefault());
    }

    // joystick: el stick sale donde pones el dedo dentro de la zona izquierda (mas comodo que uno fijo)
    const zone = root.querySelector('.tc-stick');
    const base = root.querySelector('.tc-base');
    const knob = root.querySelector('.tc-knob');
    let pid = null, cx = 0, cy = 0;
    const R = 62;
    const DEAD = 0.32;
    function place(x, y) {
      const r = zone.getBoundingClientRect();
      base.style.left = (x - r.left) + 'px';
      base.style.top = (y - r.top) + 'px';
    }
    function apply(dx, dy) {
      const len = Math.hypot(dx, dy);
      const k = len > R ? R / len : 1;
      knob.style.transform = `translate(${dx * k}px, ${dy * k}px)`;
      const nx = (dx * k) / R, ny = (dy * k) / R;
      setKey('KeyW', ny < -DEAD);
      setKey('KeyS', ny > DEAD);
      setKey('KeyA', nx < -DEAD);
      setKey('KeyD', nx > DEAD);
    }
    zone.addEventListener('pointerdown', (e) => {
      if (pid !== null) return;
      e.preventDefault(); e.stopPropagation();
      pid = e.pointerId;
      try { zone.setPointerCapture(pid); } catch { /* */ }
      cx = e.clientX; cy = e.clientY;
      place(cx, cy);
      base.classList.add('on');
      apply(0, 0);
    });
    zone.addEventListener('pointermove', (e) => {
      if (e.pointerId !== pid) return;
      e.preventDefault();
      apply(e.clientX - cx, e.clientY - cy);
    });
    const end = (e) => {
      if (e.pointerId !== pid) return;
      pid = null;
      base.classList.remove('on');
      knob.style.transform = '';
      for (const c of ['KeyW', 'KeyA', 'KeyS', 'KeyD']) setKey(c, false);
    };
    zone.addEventListener('pointerup', end);
    zone.addEventListener('pointercancel', end);
  }

  function refresh() {
    build();
    if (!root) return;
    const h = host();
    const show = !!h && fsEl() === h && !h.classList.contains('building');
    if (root.hidden === !show) return;
    root.hidden = !show;
    if (!show) releaseAll();
    else canvas()?.focus();
  }

  document.addEventListener('fullscreenchange', () => setTimeout(refresh, 60));
  document.addEventListener('webkitfullscreenchange', () => setTimeout(refresh, 60));
  window.addEventListener('blur', releaseAll);
  const watch = () => {
    const h = host();
    if (!h) return false;
    new MutationObserver(() => { refresh(); syncBox(); }).observe(h, { attributes: true, attributeFilter: ['class'] });
    refresh();
    syncBox();
    return true;
  };
  if (!watch()) document.addEventListener('DOMContentLoaded', watch);

  // ---------------------------------------------------------------- boton: seleccion multiple (recuadro)
  let boxBtn = null;
  function buildBoxBtn() {
    const h = host();
    if (!h || boxBtn) return;
    boxBtn = document.createElement('button');
    boxBtn.className = 'frame-btn tc-boxbtn';
    boxBtn.title = 'Seleccionar varios (recuadro)';
    boxBtn.hidden = true;
    boxBtn.innerHTML = '<svg class="i" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-dasharray="3 3"><rect x="4" y="5" width="16" height="14" rx="1.5"/></svg>';
    boxBtn.addEventListener('pointerdown', (e) => e.stopPropagation());
    boxBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const ed = window.__editor;
      if (!ed) return;
      ed.boxMode = !ed.boxMode;
      syncBox();
      if (window.AndroidBridge.showToast) window.AndroidBridge.showToast(ed.boxMode ? 'Recuadro activado: arrastra para seleccionar varios' : 'Recuadro desactivado');
    });
    h.appendChild(boxBtn);
  }
  function syncBox() {
    return; // el recuadro se abre con una pulsacion larga (editor.js), sin boton
    buildBoxBtn();
    const ed = window.__editor, h = host();
    if (!boxBtn || !h) return;
    const building = h.classList.contains('building');
    boxBtn.hidden = !building;
    if (!building && ed) ed.boxMode = false;
    boxBtn.classList.toggle('on', !!(ed && ed.boxMode));
  }
  document.addEventListener('boxmode', syncBox);

  // ---------------------------------------------------------------- arrastrar desde la biblioteca
  // El dedo sobre una ficha de la biblioteca: arrastre horizontal = llevar el objeto al nivel;
  // arrastre vertical = desplazar la lista (touch-action: pan-y en mobile.css).
  let drag = null;
  let ghost = null;

  function tileOf(t) { return t && t.closest ? t.closest('#library button:not(.lib-tab)') : null; }

  function showGhost(tile, x, y) {
    if (!ghost) {
      ghost = document.createElement('div');
      ghost.className = 'tc-drag';
      const img = tile.querySelector('img');
      ghost.innerHTML = img ? `<img src="${img.src}" alt="">` : `<b>${(tile.textContent || '').trim().slice(0, 2)}</b>`;
      document.body.appendChild(ghost);
    }
    ghost.style.transform = `translate(${x - 28}px, ${y - 28}px)`;
  }
  function overGame(x, y) {
    const o = document.getElementById('overlay');
    if (!o) return false;
    const r = o.getBoundingClientRect();
    return x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
  }
  function endDrag() {
    drag = null;
    if (ghost) { ghost.remove(); ghost = null; }
  }

  document.addEventListener('pointerdown', (e) => {
    if (e.pointerType !== 'touch' || !e.isPrimary) return;
    const tile = tileOf(e.target);
    if (!tile) return;
    drag = { tile, id: e.pointerId, x: e.clientX, y: e.clientY, started: false };
  }, true);

  document.addEventListener('pointermove', (e) => {
    if (!drag || e.pointerId !== drag.id) return;
    if (!drag.started) {
      const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
      if (dx > 14 && dx > Math.abs(dy) * 1.2) {
        // pulsar la ficha pone el objeto "en la mano" (como el clic de escritorio); luego lo lleva el editor
        drag.tile.click();
        const ed = window.__editor;
        if (ed && ed.beginTouchPlace(drag.id)) {
          drag.started = true;
        } else {
          endDrag();
        }
      }
      return;
    }
    if (overGame(e.clientX, e.clientY)) { if (ghost) ghost.style.display = 'none'; }
    else showGhost(drag.tile, e.clientX, e.clientY);
  }, true);

  const stop = (e) => { if (drag && e.pointerId === drag.id) setTimeout(endDrag, 0); };
  document.addEventListener('pointerup', stop, true);
  document.addEventListener('pointercancel', stop, true);

  window.addEventListener('contextmenu', (e) => e.preventDefault());
})();
