<script lang="typescript">
    import type { I18n } from "anki/i18n";
    import type { TableDatum } from "./graph-helpers";

    export let i18n: I18n;
    export let tableData: TableDatum[];
</script>

<div class="centered">
    <table dir={i18n.direction()}>
        {#each tableData as { label, value }}
            <tr>
                <td class="align-end">{label}:</td>
                <td class="align-start">{value}</td>
            </tr>
        {/each}
    </table>
</div>
