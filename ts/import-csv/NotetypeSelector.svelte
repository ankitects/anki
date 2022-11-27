<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Notetypes } from "@tslib/proto";
    
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";

    export let notetypeNameIds: Notetypes.NotetypeNameId[];
    export let notetypeId: number;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.notetypesNotetype()}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={notetypeId}>
            {#each notetypeNameIds as { id, name }}
                <option value={id}>{name}</option>
            {/each}
        </select>
    </Col>
</Row>
