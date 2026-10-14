// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type TooltipState = { show: boolean; x: number };

const mounted = vi.hoisted(() => ({ props: null as TooltipState | null }));

vi.mock("svelte", () => ({
    mount: vi.fn((_component: unknown, options: { props: TooltipState }) => {
        mounted.props = options.props;
        return {};
    }),
}));
vi.mock("./Tooltip.svelte", () => ({ default: {} }));

describe("graph tooltips when the viewport changes", () => {
    let showTooltip: typeof import("./tooltip-utils.svelte").showTooltip;

    beforeEach(async () => {
        vi.resetModules();
        mounted.props = null;
        vi.stubGlobal("window", new EventTarget());
        vi.stubGlobal("document", {
            createElement: () => ({}),
            body: { appendChild: vi.fn() },
        });
        ({ showTooltip } = await import("./tooltip-utils.svelte"));
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    it("hides a stale tooltip and allows it to show at a new position", () => {
        showTooltip("old position", 1100, 100);
        expect(mounted.props?.show).toBe(true);

        window.dispatchEvent(new Event("resize"));
        expect(mounted.props?.show).toBe(false);

        vi.advanceTimersByTime(20);
        showTooltip("new position", 300, 100);
        expect(mounted.props?.show).toBe(true);
        expect(mounted.props?.x).toBe(300);
    });

    it("cancels a pending hover update when the viewport is resized", () => {
        showTooltip("first", 1100, 100);
        showTooltip("pending", 1150, 100);

        window.dispatchEvent(new Event("resize"));
        vi.advanceTimersByTime(20);
        expect(mounted.props?.show).toBe(false);
    });
});
