<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Decks } from "@tslib/proto";
    
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";

    export let deckNameIds: Decks.DeckNameId[];
    export let deckId: number;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.decksDeck()}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={deckId}>
            {#each deckNameIds as { id, name }}
                <option value={id}>{name}</option>
            {/each}
        </select>
    </Col>
</Row>
