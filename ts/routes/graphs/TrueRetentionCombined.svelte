<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as t9n from "@generated/ftl";
    import { localizedNumber } from "@tslib/i18n";

    import { RevlogRange } from "./graph-helpers";
    import {
        calculateRetentionPercentageString,
        type PeriodTrueRetentionData,
        type TrueRetentionData,
    } from "./true-retention";

    interface Props {
        revlogRange: RevlogRange;
        data: PeriodTrueRetentionData;
    }

    let { revlogRange, data }: Props = $props();
</script>

{#snippet row(periodTitle: string, data: TrueRetentionData)}
    {@const totalPassed = data.youngPassed + data.maturePassed}
    {@const totalFailed = data.youngFailed + data.matureFailed}

    <tr>
        <th scope="row" class="row-header">{periodTitle}</th>

        <td class="young">
            {calculateRetentionPercentageString(data.youngPassed, data.youngFailed)}
        </td>
        <td class="mature">
            {calculateRetentionPercentageString(data.maturePassed, data.matureFailed)}
        </td>
        <td class="total">
            {calculateRetentionPercentageString(totalPassed, totalFailed)}
        </td>

        <td class="count">{localizedNumber(totalPassed + totalFailed)}</td>
    </tr>
{/snippet}

<table>
    <thead>
        <tr>
            <td></td>
            <th scope="col" class="col-header young">
                {t9n.statisticsCountsYoungCards()}
            </th>
            <th scope="col" class="col-header mature">
                {t9n.statisticsCountsMatureCards()}
            </th>
            <th scope="col" class="col-header total">
                {t9n.statisticsCountsTotalCards()}
            </th>
            <th scope="col" class="col-header count">
                {t9n.statisticsTrueRetentionCount()}
            </th>
        </tr>
    </thead>

    <tbody>
        {@render row(t9n.statisticsTrueRetentionToday(), data.today)}
        {@render row(t9n.statisticsTrueRetentionYesterday(), data.yesterday)}
        {@render row(t9n.statisticsTrueRetentionWeek(), data.week)}
        {@render row(t9n.statisticsTrueRetentionMonth(), data.month)}
        {@render row(t9n.statisticsTrueRetentionYear(), data.year)}

        {#if revlogRange === RevlogRange.All}
            {@render row(t9n.statisticsTrueRetentionAllTime(), data.allTime)}
        {/if}
    </tbody>
</table>

<style src="./true-retention.scss" lang="scss"></style>
