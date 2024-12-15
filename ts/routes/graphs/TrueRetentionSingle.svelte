<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as t9n from "@generated/ftl";
    import { RevlogRange } from "./graph-helpers";
    import {
        calculateRetentionPercentageString,
        getFailed,
        getPassed,
        type PeriodTrueRetentionData,
        Scope,
        type TrueRetentionData,
    } from "./true-retention";
    import { localizedNumber } from "@tslib/i18n";

    interface Props {
        revlogRange: RevlogRange;
        data: PeriodTrueRetentionData;
        scope: Scope;
    }

    let { revlogRange, data, scope }: Props = $props();
</script>

{#snippet row(periodTitle: string, data: TrueRetentionData, scope: Scope)}
    {@const passed = getPassed(data, scope)}
    {@const failed = getFailed(data, scope)}

    <tr>
        <th scope="row" class="row-header">{periodTitle}</th>

        <td class="pass">{localizedNumber(passed)}</td>
        <td class="fail">{localizedNumber(failed)}</td>
        <td class="retention">{calculateRetentionPercentageString(passed, failed)}</td>
    </tr>
{/snippet}

<table>
    <thead>
        <tr>
            <td></td>
            <th scope="col" class="col-header pass">
                {t9n.statisticsTrueRetentionPass()}
            </th>
            <th scope="col" class="col-header fail">
                {t9n.statisticsTrueRetentionFail()}
            </th>
            <th scope="col" class="col-header retention">
                {t9n.statisticsTrueRetentionRetention()}
            </th>
        </tr>
    </thead>

    <tbody>
        {@render row(t9n.statisticsTrueRetentionToday(), data.today, scope)}
        {@render row(t9n.statisticsTrueRetentionYesterday(), data.yesterday, scope)}
        {@render row(t9n.statisticsTrueRetentionWeek(), data.week, scope)}
        {@render row(t9n.statisticsTrueRetentionMonth(), data.month, scope)}
        {@render row(t9n.statisticsTrueRetentionYear(), data.year, scope)}

        {#if revlogRange === RevlogRange.All}
            {@render row(t9n.statisticsTrueRetentionAllTime(), data.allTime, scope)}
        {/if}
    </tbody>
</table>

<style src="./true-retention.scss" lang="scss"></style>
