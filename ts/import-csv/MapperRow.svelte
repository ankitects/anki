<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";

    export let label: string;
    export let options: string[];
    export let index: number = 0;

    const labelIndex = options.indexOf(label);
    index = labelIndex > 0 ? labelIndex : index;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {label}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={index}>
            {#each options as name, idx}
                <option value={idx}>{name}</option>
            {/each}
        </select>
    </Col>
</Row>
