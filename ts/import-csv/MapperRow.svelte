<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import type { ColumnOption } from "./lib";

    export let label: string;
    export let columnOptions: ColumnOption[];
    export let value: number;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {label}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value>
            {#each columnOptions as { label, value, disabled }}
                <option {value} {disabled}>{label}</option>
            {/each}
        </select>
    </Col>
</Row>
