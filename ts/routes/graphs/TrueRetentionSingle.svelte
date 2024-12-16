<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as t9n from "@generated/ftl";
    import { type RevlogRange } from "./graph-helpers";
    import {
        calculateRetentionPercentageString,
        getFailed,
        getPassed,
        getRowData,
        type PeriodTrueRetentionData,
        type RowData,
        type Scope,
    } from "./true-retention";
    import { localizedNumber } from "@tslib/i18n";

    interface Props {
        revlogRange: RevlogRange;
        data: PeriodTrueRetentionData;
        scope: Scope;
    }

    const { revlogRange, data, scope }: Props = $props();

    const rowData: RowData[] = $derived(getRowData(data, revlogRange));
</script>

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
        {#each rowData as row}
            {@const passed = getPassed(row.data, scope)}
            {@const failed = getFailed(row.data, scope)}

            <tr>
                <th scope="row" class="row-header">{row.title}</th>

                <td class="pass">{localizedNumber(passed)}</td>
                <td class="fail">{localizedNumber(failed)}</td>
                <td class="retention">
                    {calculateRetentionPercentageString(passed, failed)}
                </td>
            </tr>
        {/each}
    </tbody>
</table>

<style src="./true-retention.scss" lang="scss"></style>
