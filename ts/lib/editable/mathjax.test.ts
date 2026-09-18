// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";

const VENDOR_SRC = "/_anki/js/vendor/mathjax/tex-svg-full.js";

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

/** Stand in for MathJax having evaluated, returning a non-empty 32px-high svg. */
function fakeMathjax() {
    window.MathJax = {
        startup: { promise: Promise.resolve() },
        tex2svg: vi.fn(() => {
            const wrapper = document.createElement("div");
            wrapper.innerHTML = "<svg viewBox=\"0 0 100 32\"><g></g></svg>";
            const svg = wrapper.children[0] as unknown as SVGElement;
            Object.defineProperty(svg, "viewBox", { value: { baseVal: { height: 32 } } });
            return wrapper;
        }),
    };
}

beforeEach(() => {
    vi.resetModules();
    vi.restoreAllMocks();
    delete window.MathJax;
    document.head.querySelectorAll("script").forEach((node) => node.remove());
});

describe("editor mathjax lazy loading", () => {
    it("does not load mathjax to render an empty element", async () => {
        const scripts = spyOnScripts();
        const { convertMathjax } = await import("./mathjax");

        const [html, title] = await convertMathjax("   ", false, 20);

        expect(title).toBe("MathJax");
        expect(html).toContain("<svg");
        expect(scripts).toHaveLength(0);
    });

    it("loads the vendored bundle the first time something is typeset", async () => {
        const scripts = spyOnScripts();
        const { convertMathjax } = await import("./mathjax");

        const pending = convertMathjax("a^2", false, 20);

        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        expect(scripts[0].src).toContain(VENDOR_SRC);

        fakeMathjax();
        scripts[0].onload?.(new Event("load"));

        const [html] = await pending;
        expect(html).toContain("<svg");
        expect(window.MathJax.tex2svg).toHaveBeenCalledOnce();
    });

    it("only loads mathjax once", async () => {
        const scripts = spyOnScripts();
        const { convertMathjax } = await import("./mathjax");

        const first = convertMathjax("a^2", false, 20);
        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        fakeMathjax();
        scripts[0].onload?.(new Event("load"));
        await first;

        await convertMathjax("b^2", false, 20);
        await convertMathjax("c^2", false, 20);

        expect(scripts).toHaveLength(1);
    });

    it("reports an error instead of throwing if the bundle fails to load", async () => {
        const scripts = spyOnScripts();
        const { convertMathjax } = await import("./mathjax");

        const pending = convertMathjax("a^2", false, 20);
        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        scripts[0].onerror?.(new Event("error"));

        const [html, title] = await pending;
        expect(html).toBe("MathJax Error");
        expect(title).toContain("Failed to load MathJax");
    });

    it("does not re-inject if mathjax was already loaded eagerly", async () => {
        fakeMathjax();
        const scripts = spyOnScripts();
        const { convertMathjax } = await import("./mathjax");

        // still injects once, then reuses; the point is a single injection
        const pending = convertMathjax("a^2", false, 20);
        await vi.waitFor(() => expect(scripts).toHaveLength(1));
        scripts[0].onload?.(new Event("load"));
        await pending;

        await convertMathjax("d^2", false, 20);
        expect(scripts).toHaveLength(1);
    });
});
