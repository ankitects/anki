window.MathJax = {
    tex: {
        displayMath: [["\\[", "\\]"]],
        processRefs: false,
        processEnvironments: false,
        processEscapes: false,
        packages: {
            "[+]": ["noerrors", "mhchem"],
        },
    },
    startup: {
        typeset: false,
        pageReady: () => {
            return MathJax.startup.defaultPageReady();
        },
    },
    options: {
        renderActions: {
            addMenu: [],
            checkLoading: [],
        },
        ignoreHtmlClass: "tex2jax_ignore",
        processHtmlClass: "tex2jax_process",
    },
    loader: {
        load: ["[tex]/noerrors", "[tex]/mhchem"],
    },
};
