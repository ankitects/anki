<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import * as tr from "../lib/ftl";
    import type { Notetypes } from "../lib/proto";

    export let notetypeNameIds: Notetypes.NotetypeNameId[];
    export let notetypeId: number;

    let index = notetypeNameIds.findIndex((entry) => entry.id === notetypeId);
    $: notetypeId = notetypeNameIds[index].id;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.notetypesNotetype()}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={index}>
            {#each notetypeNameIds as { name }, idx}
                <option value={idx}>{name}</option>
            {/each}
        </select>
    </Col>
</Row>
