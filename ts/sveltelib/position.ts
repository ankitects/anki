// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { computePosition, autoUpdate } from "@floating-ui/dom";
import type { Placement, ComputePositionReturn } from "@floating-ui/dom";

interface FloatingItem {
    element: HTMLElement;
    destroy(): void;
}

const floatingElements: Map<EventTarget, () => void> = new Map();

export function closeOnClick(this: HTMLElement, event: MouseEvent): void {
    if (!event.target) {
        return;
    }

    if (floatingElements.has(event.target)) {
        floatingElements.get(event.target)!();
    }
}

export function closeOnKeyup(this: HTMLElement, event: KeyboardEvent): void {
    if (!event.target) {
        return;
    }

    if (floatingElements.has(event.target)) {
        floatingElements.get(event.target)!();
    }
}

interface PositionArgs {
    floating: HTMLElement;
    placement: Placement;
}

function position(
    reference: HTMLElement,
    positionArgs: PositionArgs,
): { update(args: PositionArgs): void; destroy(): void } {
    let args = positionArgs;

    function updateInner(): Promise<void> {
        return computePosition(reference, args.floating, {
            placement: args.placement,
        }).then(({ x, y }: ComputePositionReturn): void => {
            Object.assign(args.floating.style, {
                left: `${x}px`,
                top: `${y}px`,
            });
        });
    }

    let cleanup: () => void;

    function destroy(): void {
        cleanup?.();

        if (!args.floating) {
            return;
        }

        floatingElements.delete(args.floating);
        args.floating.hidden = true;
    }

    function update(updateArgs: PositionArgs): void {
        destroy();
        args = updateArgs;

        if (!args.floating) {
            return;
        }

        cleanup = autoUpdate(reference, args.floating, updateInner);
        floatingElements.set(args.floating, destroy);
        args.floating.hidden = false;
    }

    update(args);

    return {
        update,
        destroy,
    };
}

export default position;
