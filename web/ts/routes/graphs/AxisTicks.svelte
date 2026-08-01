<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphBounds } from "./graph-helpers";

    export let bounds: GraphBounds;
</script>

<g class="x-ticks" transform={`translate(0, ${bounds.height - bounds.marginBottom})`} />
<g class="y-ticks" transform={`translate(${bounds.marginLeft}, 0)`} />
<g class="y2-ticks" transform={`translate(${bounds.width - bounds.marginRight}, 0)`} />

<style lang="scss">
    g :global(.domain) {
        opacity: 0.05;
    }
</style>
