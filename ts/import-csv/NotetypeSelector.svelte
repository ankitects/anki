<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { NotetypeNameId } from "@tslib/anki/notetypes_pb";
    import * as tr from "@tslib/ftl";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";

    export let notetypeNameIds: NotetypeNameId[];
    export let notetypeId: bigint;

    $: label = notetypeNameIds.find((n) => n.id === notetypeId)?.name;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.notetypesNotetype()}
    </Col>
    <Col --col-size={1}>
        <Select bind:value={notetypeId} {label}>
            {#each notetypeNameIds as { id, name }}
                <SelectOption value={id}>{name}</SelectOption>
            {/each}
        </Select>
    </Col>
</Row>
