<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/contextKeys";
    import Tooltip from "bootstrap/js/dist/tooltip";

    export let tooltip: string;

    function createTooltip(element: HTMLElement): void {
        element.title = tooltip;
        new Tooltip(element, {
            placement: "bottom",
            html: true,
            offset: [0, 20],
        });
    }

    const nightMode = getContext<boolean>(nightModeKey);
</script>

<span class:night-mode={nightMode}><slot {createTooltip} /></span>

<style lang="scss">
    span {
        text-decoration: underline dashed;
        text-decoration-color: rgba(0 0 0 / 0.5);
    }

    .night-mode {
        text-decoration-color: rgba(255 255 255 / 0.5);
    }
</style>
