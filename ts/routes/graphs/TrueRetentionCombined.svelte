<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as t9n from "@generated/ftl";
    import { localizedNumber } from "@tslib/i18n";

    import { type RevlogRange } from "./graph-helpers";
    import {
        calculateRetentionPercentageString,
        getRowData,
        type PeriodTrueRetentionData,
        type RowData,
    } from "./true-retention";

    interface Props {
        revlogRange: RevlogRange;
        data: PeriodTrueRetentionData;
    }

    const { revlogRange, data }: Props = $props();

    const rowData: RowData[] = $derived(getRowData(data, revlogRange));
</script>

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
        {#each rowData as row}
            {@const totalPassed = row.data.youngPassed + row.data.maturePassed}
            {@const totalFailed = row.data.youngFailed + row.data.matureFailed}

            <tr>
                <th scope="row" class="row-header">{row.title}</th>

                <td class="young">
                    {calculateRetentionPercentageString(
                        row.data.youngPassed,
                        row.data.youngFailed,
                    )}
                </td>
                <td class="mature">
                    {calculateRetentionPercentageString(
                        row.data.maturePassed,
                        row.data.matureFailed,
                    )}
                </td>
                <td class="total">
                    {calculateRetentionPercentageString(totalPassed, totalFailed)}
                </td>

                <td class="count">{localizedNumber(totalPassed + totalFailed)}</td>
            </tr>
        {/each}
    </tbody>
</table>

<style src="./true-retention.scss" lang="scss"></style>
