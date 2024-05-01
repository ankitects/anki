<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportResponse } from "@generated/anki/import_export_pb";
    import * as tr from "@generated/ftl";

    import Container from "$lib/components/Container.svelte";
    import Row from "$lib/components/Row.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";

    import DetailsTable from "./DetailsTable.svelte";
    import { getSummaries } from "./lib";
    import QueueSummary from "./QueueSummary.svelte";

    export let response: ImportResponse;
    $: summaries = getSummaries(response.log!);
    $: foundNotes = response.log?.foundNotes ?? 0;

    const gutterBlockSize = 0.5;
    const computedStyle = getComputedStyle(document.documentElement);
    const rootFontSize = parseInt(computedStyle.fontSize);
    // Container padding + Row padding + Row margin + TitledContainer padding
    const bottomOffset = (3 * gutterBlockSize + 0.75) * rootFontSize;
</script>

<Container
    breakpoint="sm"
    --gutter-inline="0.25rem"
    --gutter-block={`${gutterBlockSize}rem`}
>
    <Row>
        <TitledContainer title={tr.importingOverview()}>
            <p>
                {tr.importingNotesFoundInFile2({
                    notes: foundNotes,
                })}
            </p>
            <ul>
                {#each summaries as summary}
                    <QueueSummary {summary} />
                {/each}
            </ul>
        </TitledContainer>
    </Row>
    <Row>
        <TitledContainer title={tr.importingDetails()}>
            <DetailsTable {summaries} {bottomOffset} />
        </TitledContainer>
    </Row>
</Container>
