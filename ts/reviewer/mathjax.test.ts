// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock(import("@tslib/bridgecommand"), () => ({ bridgeCommand: vi.fn() }));

const CONFIG_SRC = "/_anki/js/mathjax.js";
const VENDOR_SRC = "/_anki/js/vendor/mathjax/tex-chtml-full.js";
const HTML_WITH_MATHJAX = "\\(rdrd\\theta\\)";
const HTML_WITH_MATHJAX_SQUARE = "\\[abc\\]";
const HTML_WITHOUT_MATHJAX = "<i>[blah]</i>";

declare global {
    interface Window {
        MathJax?: any;
    }
}

function spyOnScripts(): HTMLScriptElement[] {
    const scripts: HTMLScriptElement[] = [];
    vi.spyOn(document.head, "appendChild").mockImplementation((node: Node) => {
        if (node instanceof HTMLScriptElement) {
            scripts.push(node);
        }
        return node;
    });
    return scripts;
}

beforeAll(async () => {
    await import("./index"); // warm cache
}, 10000);

beforeEach(() => {
    vi.resetModules();
    delete window.MathJax;
    document.body.innerHTML = "<div id=\"qa\"></div>";
    document.head.querySelectorAll("script").forEach((node) => node.remove());
});

describe("mathjax lazy loading", () => {
    it("doesnt load mathjax for cards without it", async () => {
        const scripts = spyOnScripts();
        const { _updateQA } = await import("./index");

        const cb = vi.fn();
        _updateQA(HTML_WITHOUT_MATHJAX, null, () => null, cb);
        await vi.waitFor(() => expect(cb).toHaveBeenCalledOnce());
        expect(scripts).toHaveLength(0);
    });

    it("loads mathjax.js first, followed by actual mathjax logic", async () => {
        const scripts = spyOnScripts();
        const { _showQuestion } = await import("./index");

        _showQuestion(HTML_WITH_MATHJAX, "", "");

        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        expect(scripts[0].src).toContain(CONFIG_SRC);
        scripts[0].onload?.(new Event("load")); // mock script loading
        await vi.waitFor(() => expect(scripts).toHaveLength(2));
        expect(scripts[1].src).toContain(VENDOR_SRC);
    });

    it("doesnt reload once lazy-loaded", async () => {
        const scripts = spyOnScripts();
        const { _updateQA } = await import("./index");

        const cb = vi.fn();
        _updateQA(HTML_WITH_MATHJAX, null, () => null, cb);
        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        scripts[0].onload?.(new Event("load"));
        await vi.waitFor(() => expect(scripts).toHaveLength(2));
        scripts[1].onload?.(new Event("load"));
        await vi.waitFor(() => expect(cb).toHaveBeenCalledOnce());

        _updateQA(HTML_WITHOUT_MATHJAX, null, () => null, cb);
        await vi.waitFor(() => expect(cb).toHaveBeenCalledTimes(2));
        expect(scripts).toHaveLength(2);

        _updateQA(HTML_WITH_MATHJAX_SQUARE, null, () => null, cb);
        await vi.waitFor(() => expect(cb).toHaveBeenCalledTimes(3));
        expect(scripts).toHaveLength(2);
    });

    it("doesnt reload if eagerly loaded", async () => {
        const scripts = spyOnScripts();
        window.MathJax = {
            startup: { promise: Promise.resolve() },
            typesetClear: vi.fn(),
            typesetPromise: vi.fn().mockResolvedValue(undefined),
        };
        const { _updateQA } = await import("./index");
        const cb = vi.fn();
        _updateQA(HTML_WITH_MATHJAX, null, () => null, cb);
        await vi.waitFor(() => expect(cb).toHaveBeenCalledOnce());
        expect(scripts).toHaveLength(0);
    });

    it("doesnt reload if already loading", async () => {
        const scripts = spyOnScripts();
        const { _showQuestion, _updateQA } = await import("./index");

        _showQuestion(HTML_WITH_MATHJAX, HTML_WITH_MATHJAX, "");

        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        scripts[0].onload?.(new Event("load"));
        await vi.waitFor(() => expect(scripts).toHaveLength(2));

        const cb = vi.fn();
        _updateQA(HTML_WITH_MATHJAX_SQUARE, null, () => null, cb);
        await vi.waitFor(() => expect(scripts).toHaveLength(2));
        expect(cb).toHaveBeenCalledTimes(0);
        scripts[1].onload?.(new Event("load"));
        await vi.waitFor(() => expect(cb).toHaveBeenCalled());
    });

    it("preloads if needed by answer while on question", async () => {
        const scripts = spyOnScripts();
        const { _showQuestion } = await import("./index");
        _showQuestion(HTML_WITHOUT_MATHJAX, HTML_WITH_MATHJAX_SQUARE, "");
        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        expect(scripts[0].src).toContain(CONFIG_SRC);
    });
});
