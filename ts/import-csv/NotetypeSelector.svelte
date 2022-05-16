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
    import type { Notetypes } from "../lib/proto";

    export let notetypeNameIds: Notetypes.NotetypeNameId[];
    export let notetypeId: number;

    function updateCurrentId(event: Event) {
        const index = parseInt((event.target! as HTMLSelectElement).value);
        notetypeId = notetypeNameIds[index].id;
    }
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        <div>{tr.notetypesNotetype()}</div>
    </Col>
    <Col --col-size={1}>
        <ButtonGroup class="flex-grow-1">
            <SelectButton class="flex-grow-1" on:change={updateCurrentId}>
                {#each notetypeNameIds as entry, idx}
                    <SelectOption
                        value={String(idx)}
                        selected={entry.id === notetypeId}
                    >
                        {entry.name}
                    </SelectOption>
                {/each}
            </SelectButton>
        </ButtonGroup>
    </Col>
</Row>
