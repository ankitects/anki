import { mathjax } from "mathjax-full/js/mathjax";
import { TeX } from "mathjax-full/js/input/tex";
import { CHTML } from "mathjax-full/js/output/chtml";
import { HTMLAdaptor } from "mathjax-full/js/adaptors/HTMLAdaptor";
import { RegisterHTMLHandler } from "mathjax-full/js/handlers/html";

import { AllPackages } from "mathjax-full/js/input/tex/AllPackages.js";
import "mathjax-full/js/util/entities/all";

// @ts-expect-error Minor interface mismatch: document.documentElement.nodeValue might be null
const adaptor = new HTMLAdaptor(window);
RegisterHTMLHandler(adaptor);

const texOptions = {
    displayMath: [["\\[", "\\]"]],
    processRefs: false,
    processEnvironments: false,
    processEscapes: false,
    packages: AllPackages,
};

export function convertMathJax(input: string): string {
    const tex = new TeX(texOptions);
    const chtml = new CHTML({
        fontURL: "/_anki/js/vendor/mathjax/output/chtml/fonts/woff-v2",
    });

    const html = mathjax.document(input, { InputJax: tex, OutputJax: chtml });
    html.render();

    return (
        adaptor.innerHTML(adaptor.head(html.document)) +
        adaptor.innerHTML(adaptor.body(html.document))
    );
}
