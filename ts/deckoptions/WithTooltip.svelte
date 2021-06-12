<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onDestroy } from "svelte";
    import Tooltip from "bootstrap/js/dist/tooltip";

    type TriggerType =
        | "hover focus"
        | "click"
        | "hover"
        | "focus"
        | "manual"
        | "click hover"
        | "click focus"
        | "click hover focus";

    export let tooltip: string;
    export let trigger: TriggerType = "hover focus";

    let tooltipObject: Tooltip;

    function createTooltip(element: HTMLElement): void {
        element.title = tooltip;
        tooltipObject = new Tooltip(element, {
            placement: "bottom",
            html: true,
            offset: [0, 20],
            delay: { show: 250, hide: 0 },
            trigger,
        });
    }

    onDestroy(() => {
        if (tooltipObject) {
            tooltipObject.dispose();
        }
    });
</script>

<slot {createTooltip} {tooltipObject} />
