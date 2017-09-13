window.MathJax = {
    jax: ["input/TeX", "output/CommonHTML"],
    extensions: ["tex2jax.js"],
    TeX: {
        extensions: ["AMSmath.js", "AMSsymbols.js", "noErrors.js", "noUndefined.js", "mhchem.js"]
    },
    tex2jax: {
        displayMath: [["\\[", "\\]"]],
        processRefs: false,
        processEnvironments: false,
    },
    messageStyle: "none",
    skipStartupTypeset: true,
    showMathMenu: false,
    AuthorInit: function () {
        MathJax.Hub.processSectionDelay = 0;
        MathJax.Hub.processUpdateTime = 0;
        MathJax.Hub.processUpdateDelay = 0;
    }
};
