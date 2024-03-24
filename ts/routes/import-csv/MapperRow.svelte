<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "$lib/components/Col.svelte";
    import Row from "$lib/components/Row.svelte";
    import Select from "$lib/components/Select.svelte";

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
        <Select
            bind:value
            {label}
            list={columnOptions}
            parser={(item) => ({
                content: item.label,
                value: item.value,
                disabled: item.disabled,
            })}
        />
    </Col>
</Row>
