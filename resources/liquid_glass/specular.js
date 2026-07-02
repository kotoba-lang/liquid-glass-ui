/* liquid-glass pointer-tracking specular — optional progressive enhancement.
 *
 * docs/design.md reserves the `liquid-glass__specular` marker span as "the
 * seam this would attach to (a pointer/device-motion-driven highlight
 * position, or a GPU-composited surface)". This script is that seam's
 * smallest tenant: ONE document-level, rAF-throttled pointermove listener
 * that writes --liquid-glass-pointer-x / --liquid-glass-pointer-y (0..1,
 * relative to the hovered glass element's rect) onto the element and marks it
 * [data-lg-pointer]. All visuals live in liquid-glass.style CSS, gated behind
 * the `.liquid-glass-js` class this script adds to <html> — without the
 * script (or under prefers-reduced-motion, or without JS at all) the span
 * keeps its display:none default and nothing changes. Zero deps, no state
 * machine, no framework: the library still owns no interaction logic.
 *
 * Host selector: prefer this script tag's data-lg-selector attribute (emit it
 * from liquid-glass.style/specular-selector — one source of truth); when
 * absent, derive the same list from the marker spans present in the document.
 */
(function () {
  'use strict';
  if (typeof document === 'undefined' || !window.requestAnimationFrame) return;
  // Respect reduced motion: never attach (CSS re-disables the highlight too).
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  var script = document.currentScript;

  function derivedSelector() {
    var seen = Object.create(null);
    var marks = document.querySelectorAll('.liquid-glass__specular');
    for (var i = 0; i < marks.length; i++) {
      var host = marks[i].parentElement;
      if (!host) continue;
      for (var j = 0; j < host.classList.length; j++) {
        var c = host.classList[j];
        if (c.indexOf('liquid-glass__') === 0 && c.indexOf('--') === -1) seen['.' + c] = true;
      }
    }
    return Object.keys(seen).join(',');
  }

  var selector = (script && script.dataset && script.dataset.lgSelector) || derivedSelector();
  if (!selector) return;

  document.documentElement.classList.add('liquid-glass-js');

  var active = null, pending = null, raf = 0;

  function clearActive() {
    if (active) { active.removeAttribute('data-lg-pointer'); active = null; }
  }

  function frame() {
    raf = 0;
    var e = pending;
    pending = null;
    if (!e) return;
    var host = e.target && e.target.closest ? e.target.closest(selector) : null;
    if (host !== active) clearActive();
    if (!host) return;
    var r = host.getBoundingClientRect();
    if (!r.width || !r.height) return;
    var x = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    var y = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height));
    host.style.setProperty('--liquid-glass-pointer-x', x.toFixed(3));
    host.style.setProperty('--liquid-glass-pointer-y', y.toFixed(3));
    host.setAttribute('data-lg-pointer', '');
    active = host;
  }

  document.addEventListener('pointermove', function (e) {
    pending = e;
    if (!raf) raf = requestAnimationFrame(frame);
  }, { passive: true });

  document.addEventListener('pointerleave', clearActive, { passive: true });
})();
