(function () {
  var RTL_LANGS = ['ar', 'fa', 'he', 'ug', 'yi'];

  function applyDirection() {
    var lang = window.location.pathname.split('/')[1];
    if (RTL_LANGS.indexOf(lang) !== -1) {
      document.documentElement.classList.add('is-rtl');
    } else {
      document.documentElement.classList.remove('is-rtl');
    }
  }

  applyDirection();

  // Handle browser back/forward navigation in the SPA
  window.addEventListener('popstate', applyDirection);

  // Intercept pushState/replaceState for client-side routing
  var _push = history.pushState;
  history.pushState = function () {
    _push.apply(this, arguments);
    applyDirection();
  };

  var _replace = history.replaceState;
  history.replaceState = function () {
    _replace.apply(this, arguments);
    applyDirection();
  };
})();
