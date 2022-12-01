<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Decks } from "@tslib/proto";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";

    export let deckNameIds: Decks.DeckNameId[];
    export let deckId: number;

    $: label = deckNameIds.find((d) => d.id === deckId)?.name.replace(/^.+::/, "...");
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.decksDeck()}
    </Col>
    <Col --col-size={1}>
        <Select bind:value={deckId} {label}>
            {#each deckNameIds as { id, name }}
                <SelectOption value={id}>{name}</SelectOption>
            {/each}
        </Select>
    </Col>
</Row>
