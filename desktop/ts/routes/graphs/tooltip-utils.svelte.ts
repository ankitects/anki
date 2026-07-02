// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { DebouncedFunc } from "lodash-es";
import { throttle } from "lodash-es";
import { mount } from "svelte";

import Tooltip from "./Tooltip.svelte";

type TooltipProps = {
    html: string;
    x: number;
    y: number;
    show: boolean;
};
let tooltip: Record<string, any> | null = null;
let props: TooltipProps = { html: "", x: 0, y: 0, show: false };

function getOrCreateTooltip(): TooltipProps {
    if (tooltip) {
        return props;
    }

    const target = document.createElement("div");
    const p = $state(props);
    props = p;
    tooltip = mount(Tooltip, { target, props });

    document.body.appendChild(target);

    return props;
}

function showTooltipInner(msg: string, x: number, y: number): void {
    const props = getOrCreateTooltip();
    props.html = msg;
    props.x = x;
    props.y = y;
    props.show = true;
}

export const showTooltip: DebouncedFunc<(msg: string, x: number, y: number) => void> = throttle(showTooltipInner, 16);

export function hideTooltip(): void {
    const props = getOrCreateTooltip();
    showTooltip.cancel();
    props.show = false;
}
