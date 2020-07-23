<script lang="typescript">
    import { I18n } from "../i18n";
    import { GraphBounds } from "./graphs";
    export let bounds: GraphBounds;
    export let i18n: I18n;
    const noData = i18n.tr(i18n.TR.STATISTICS_NO_DATA);
</script>

<g class="no-data">
    <rect x="0" y="0" width={bounds.width} height={bounds.height} />
    <text x="{bounds.width / 2}," y={bounds.height / 2}>{noData}</text>
</g>
