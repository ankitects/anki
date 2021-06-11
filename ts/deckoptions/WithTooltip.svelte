<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Tooltip from "bootstrap/js/dist/tooltip";

    export let tooltip: string;

    function createTooltip(element: HTMLElement): void {
        element.title = tooltip;
        new Tooltip(element, {
            placement: "bottom",
            html: true,
            offset: [0, 20],
            delay: { show: 250, hide: 0 },
        });
    }
</script>

<slot {createTooltip} />
