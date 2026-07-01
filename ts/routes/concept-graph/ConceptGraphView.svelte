<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ConceptGraphResponse } from "@generated/anki/stats_pb";
    import { getConceptGraph } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { onMount } from "svelte";

    import ConceptGraph from "./ConceptGraph.svelte";

    // When non-zero, the graph is scoped to this deck (and its children).
    export let deckId = 0n;

    let search = "deck:current";
    let response: ConceptGraphResponse | null = null;
    let loading = true;
    let error = "";

    async function load(): Promise<void> {
        loading = true;
        error = "";
        try {
            response = await getConceptGraph({
                search: deckId === 0n ? search : "",
                deckId,
            });
        } catch (err) {
            error = String(err);
        } finally {
            loading = false;
        }
    }

    onMount(load);

    $: nodes = (response?.nodes ?? []).map((n) => ({
        id: n.id,
        label: n.label,
        cardCount: n.cardCount,
        withMemoryState: n.withMemoryState,
        averageRetrievability: n.averageRetrievability,
        reviewedCount: n.reviewedCount,
    }));
    $: edges = (response?.edges ?? []).map((e) => ({
        source: e.source,
        target: e.target,
        weight: e.weight,
    }));
</script>

<div class="concept-map">
    <div class="toolbar">
        <h1 class="title">{tr.statisticsConceptMap()}</h1>
        {#if deckId === 0n}
            <form class="search" on:submit|preventDefault={load}>
                <input
                    type="text"
                    bind:value={search}
                    placeholder={tr.statisticsConceptMapSearch()}
                    aria-label={tr.statisticsConceptMapSearch()}
                />
            </form>
        {/if}
    </div>

    <ul class="legend">
        <li>
            <span class="swatch swatch--mastered"></span>
            {tr.statisticsConceptMapMastered()}
        </li>
        <li>
            <span class="swatch swatch--learning"></span>
            {tr.statisticsConceptMapLearning()}
        </li>
        <li>
            <span class="swatch swatch--weak"></span>
            {tr.statisticsConceptMapWeak()}
        </li>
        <li>
            <span class="swatch swatch--new"></span>
            {tr.statisticsConceptMapNew()}
        </li>
    </ul>

    {#if error}
        <div class="message">{error}</div>
    {:else if !loading && nodes.length === 0}
        <div class="message">{tr.statisticsConceptMapEmpty()}</div>
    {:else}
        <ConceptGraph {nodes} {edges} />
    {/if}
</div>

<style lang="scss">
    .concept-map {
        max-width: 60em;
        margin: 0 auto;
        padding: 1em;
        color: var(--fg);
        font-size: var(--font-size);
    }

    .toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 0.5em;
        margin-bottom: 0.75em;
    }

    .title {
        font-size: 1.3em;
        font-weight: 600;
        margin: 0;
    }

    .search input {
        min-width: 16em;
        color: var(--fg);
        background: var(--canvas-inset);
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        padding: 0.35em 0.6em;

        &:focus {
            outline: none;
            border-color: var(--border-focus);
            box-shadow: var(--shadow-focus);
        }
    }

    .legend {
        display: flex;
        flex-wrap: wrap;
        gap: 1em;
        list-style: none;
        padding: 0;
        margin: 0 0 0.75em;
        color: var(--fg-subtle);

        li {
            display: flex;
            align-items: center;
            gap: 0.35em;
        }
    }

    .swatch {
        display: inline-block;
        width: 0.85em;
        height: 0.85em;
        border-radius: 50%;
        border: 1px solid var(--border-subtle);

        &--mastered {
            background: var(--state-review);
        }
        &--learning {
            background: var(--state-buried);
        }
        &--weak {
            background: var(--state-learn);
        }
        &--new {
            background: var(--state-new);
        }
    }

    .message {
        padding: 2em;
        text-align: center;
        color: var(--fg-subtle);
        background: var(--canvas-inset);
        border: 1px solid var(--border-subtle);
        border-radius: var(--border-radius-medium);
    }
</style>
