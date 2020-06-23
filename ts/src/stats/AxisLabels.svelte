<script lang="typescript">
    import { GraphBounds } from "./graphs";
    export let bounds: GraphBounds;
    export let xText: string;
    export let yText: string;
</script>

<text
    class="axis-label"
    transform={`translate(${bounds.width / 2}, ${bounds.height - 5})`}>
    {xText}
</text>
<text
    class="axis-label y-axis-label"
    transform={`translate(${bounds.marginLeft / 3}, ${(bounds.height - bounds.marginBottom) / 2 + bounds.marginTop}) rotate(-180)`}>
    {yText}
</text>
