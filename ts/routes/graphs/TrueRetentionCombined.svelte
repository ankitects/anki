<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { colorBlindColors, type GraphBounds } from "./graph-helpers";
    import * as tr from "@generated/ftl";
    import { localizedNumber } from "@tslib/i18n";
    import { type RevlogRange } from "./graph-helpers";
    import {
        calculateRetentionPercentageString,
        getRowData,
        type PeriodTrueRetentionData,
        type RowData,
    } from "./true-retention";
    import { onMount } from "svelte";

    interface Props {
        revlogRange: RevlogRange;
        data: PeriodTrueRetentionData;
    }

    const { revlogRange, data }: Props = $props();

    const rowData: RowData[] = $derived(getRowData(data, revlogRange));

    // Default (non-colorblind) colors
    let youngColor = "#64c476";
    let matureColor = "#31a354";

    onMount(() => {
        const isColorBlindMode = (window as any).colorBlindMode;
        if (isColorBlindMode) {
            youngColor = colorBlindColors.young;
            matureColor = colorBlindColors.mature;
        }
        // Set globally so scoped SCSS can use them
        document.documentElement.style.setProperty("--young-color", youngColor);
        document.documentElement.style.setProperty("--mature-color", matureColor);
    });
</script>

<table>
    <thead>
        <tr>
            <td></td>
            <th scope="col" class="col-header young">
                {tr.statisticsTrueRetentionYoung()}
            </th>
            <th scope="col" class="col-header mature">
                {tr.statisticsTrueRetentionMature()}
            </th>
            <th scope="col" class="col-header total">
                {tr.statisticsTrueRetentionTotal()}
            </th>
            <th scope="col" class="col-header count">
                {tr.statisticsTrueRetentionCount()}
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

<style lang="scss">
    @use "true-retention-base";

    .young,
    .mature,
    .total,
    .count {
        text-align: right;
    }

    .young {
        color: var(--young-color);
    }

    .mature {
        color: var(--mature-color);
    }

    .total {
        color: var(--fg);
    }

    .count {
        color: var(--fg-subtle);
    }
</style>
