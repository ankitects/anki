<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../components/ButtonGroup.svelte";
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import SelectButton from "../components/SelectButton.svelte";
    import SelectOption from "../components/SelectOption.svelte";
    import * as tr from "../lib/ftl";
    import type { Decks } from "../lib/proto";

    export let deckNameIds: Decks.DeckNameId[];
    export let deckId: number;

    function updateCurrentId(event: Event) {
        const index = parseInt((event.target! as HTMLSelectElement).value);
        deckId = deckNameIds[index].id;
    }
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        <div>{tr.decksDeck()}</div>
    </Col>
    <Col --col-size={1}>
        <ButtonGroup class="flex-grow-1">
            <SelectButton class="flex-grow-1" on:change={updateCurrentId}>
                {#each deckNameIds as entry, idx}
                    <SelectOption value={String(idx)} selected={entry.id === deckId}>
                        {entry.name}
                    </SelectOption>
                {/each}
            </SelectButton>
        </ButtonGroup>
    </Col>
</Row>
