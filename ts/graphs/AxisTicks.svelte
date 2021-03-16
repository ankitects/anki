<script lang="typescript">
    import type { GraphBounds } from "./graph-helpers";

    export let bounds: GraphBounds;
</script>

<style lang="scss">
    g :global(.domain) {
        opacity: 0.05;
    }
</style>

<g
    class="x-ticks"
    transform={`translate(0,${bounds.height - bounds.marginBottom})`} />
<g class="y-ticks" transform={`translate(${bounds.marginLeft}, 0)`} />
<g
    class="y2-ticks"
    transform={`translate(${bounds.width - bounds.marginRight}, 0)`} />
