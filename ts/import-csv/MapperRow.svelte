<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import type { ColumnOption } from "./lib";

    let rowLabel: string;
    export { rowLabel as label };

    export let columnOptions: ColumnOption[];
    export let value: number;

    $: label = columnOptions.find((o) => o.value === value)?.label;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {rowLabel}
    </Col>
    <Col --col-size={1}>
        <Select bind:value {label}>
            {#each columnOptions as { label, value, disabled }}
                <SelectOption {value} {disabled}>{label}</SelectOption>
            {/each}
        </Select>
    </Col>
</Row>
