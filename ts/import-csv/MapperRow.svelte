<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";

    export let label: string;
    export let options: string[];
    export let value: number;
    export let baseValue: number = 0;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {label}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value>
            {#each options as name, idx}
                <option value={idx + baseValue}>{name}</option>
            {/each}
        </select>
    </Col>
</Row>
