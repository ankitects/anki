<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import InputBox from "./InputBox.svelte";
    import { PercentageRangeEnum } from "./percentageRange";
    import * as tr from "@generated/ftl";

    export let range: PercentageRangeEnum;
</script>

<InputBox>
    <label>
        <input
            type="radio"
            bind:group={range}
            value={PercentageRangeEnum.Percentile50}
        />
        50%
    </label>
    <label>
        <input
            type="radio"
            bind:group={range}
            value={PercentageRangeEnum.Percentile95}
        />
        95%
    </label>
    <label>
        <input
            type="radio"
            bind:group={range}
            value={PercentageRangeEnum.Percentile100}
        />
        100%
    </label>
    <label>
        <input type="radio" bind:group={range} value={PercentageRangeEnum.All} />
        {tr.statisticsRangeAllTime()}
    </label>
</InputBox>
