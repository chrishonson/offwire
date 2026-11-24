(function () {
  // Copy build commands
  var btn = document.getElementById('copy');
  var code = document.getElementById('cmds');
  if (btn && code) {
    var text = 'git clone https://github.com/chrishonson/offwire\ncd offwire\n./gradlew :app-classic:installDebug';
    var selectAll = function () {
      try {
        var r = document.createRange();
        r.selectNodeContents(code);
        var s = window.getSelection();
        s.removeAllRanges();
        s.addRange(r);
        btn.textContent = 'Selected';
      } catch (e) {}
    };
    var reset = function () { setTimeout(function () { btn.textContent = 'Copy'; }, 1800); };
    btn.addEventListener('click', function () {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () { btn.textContent = 'Copied'; }, selectAll).then(reset);
      } else { selectAll(); reset(); }
    });
  }

  // Hero: highlight the reply clause by clause. The full text is present without this.
  var reply = document.getElementById('reply');
  var calm = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reply && !calm) {
    var cls = reply.querySelectorAll('.cl');
    var i = cls.length;
    reply.classList.add('is-live');
    var tick = function () {
      var n;
      if (i >= cls.length) {
        i = 0;
        for (n = 0; n < cls.length; n++) { cls[n].classList.add('pending'); cls[n].classList.remove('now'); }
        setTimeout(tick, 900);
        return;
      }
      for (n = 0; n < cls.length; n++) cls[n].classList.remove('now');
      cls[i].classList.remove('pending');
      cls[i].classList.add('now');
      i++;
      if (i >= cls.length) {
        setTimeout(function () { cls[cls.length - 1].classList.remove('now'); }, 1100);
        setTimeout(tick, 4200);
      } else {
        setTimeout(tick, 1100);
      }
    };
    setTimeout(tick, 2600);
  }
})();
