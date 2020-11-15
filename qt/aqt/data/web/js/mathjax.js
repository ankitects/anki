window.MathJax = {
  tex: {
    displayMath: [["\\[", "\\]"]],
    processRefs: false,
    processEnvironments: false,
    packages: [
      'base',
      'ams',
      'noerrors',
      'noundefined',
      'mhchem',
      'require',
    ]
  },
  startup: {
    typeset: false
  },
  options: {
    renderActions: {
      addMenu: [],
      checkLoading: []
    },
    ignoreHtmlClass: 'tex2jax_ignore',
    processHtmlClass: 'tex2jax_process'
  },
  loader: {
    load: [
      '[tex]/noerrors',
      '[tex]/mhchem',
      '[tex]/require',
    ]
  }
};
