<script lang="typescript">
    import { GraphBounds } from "./graphs";
    export let bounds: GraphBounds;
</script>

<g
    class="x-ticks no-domain-line"
    transform={`translate(0,${bounds.height - bounds.marginBottom})`} />
<g class="y-ticks no-domain-line" transform={`translate(${bounds.marginLeft}, 0)`} />
<g
    class="y2-ticks no-domain-line"
    transform={`translate(${bounds.width - bounds.marginRight}, 0)`} />
