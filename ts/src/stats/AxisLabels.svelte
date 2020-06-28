<script lang="typescript">
    import { GraphBounds } from "./graphs";
    import { I18n } from "../i18n";

    export let bounds: GraphBounds;
    export let xText: string;
    export let yText: string;
    export let i18n: I18n;

    let yRotate = 180;
    if (i18n.supportsVerticalText()) {
        yRotate = 0;
    }
</script>

<text
    class="axis-label"
    transform={`translate(${bounds.width / 2}, ${bounds.height - 5})`}>
    {xText}
</text>
<text
    class="axis-label y-axis-label"
    transform={`translate(${bounds.marginLeft / 3}, ${(bounds.height - bounds.marginBottom) / 2 + bounds.marginTop})
    rotate(${yRotate} 0 0)`}>
    {yText}
</text>
