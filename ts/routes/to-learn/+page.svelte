<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { NeverLearnedListResponse } from "@generated/anki/tags_pb";
    import { neverLearnedList } from "@generated/backend";
    import * as tr from "@generated/ftl";

    import TitledContainer from "$lib/components/TitledContainer.svelte";

    // Group by AAMC section. MileDown is single-rooted under "MileDown::", so
    // the sections live at depth 2 (MileDown::Behavioral, MileDown::Biochemistry,
    // ...); depth 1 would collapse everything into one "MileDown" topic.
    const groupDepth = 2;

    let data: NeverLearnedListResponse | null = null;

    async function load(): Promise<void> {
        // Empty search -> whole collection; backend ANDs in tag:NeverLearned itself.
        data = await neverLearnedList({ groupDepth, search: "" });
    }

    load();
</script>

<div class="to-learn-page">
    {#if data}
        <TitledContainer title={tr.statisticsToLearnTitle()}>
            {#if data.groups.length === 0}
                <div class="groups empty">{tr.statisticsToLearnEmpty()}</div>
            {:else}
                {#each data.groups as group (group.tag)}
                    <div class="group">
                        <h2 class="group-title">
                            {group.tag}
                            <span class="count"
                                >{tr.statisticsToLearnCount({
                                    count: group.cards.length,
                                })}</span
                            >
                        </h2>
                        <ul class="card-list">
                            {#each group.cards as card (card.cardId)}
                                <li>{card.label}</li>
                            {/each}
                        </ul>
                    </div>
                {/each}
            {/if}
        </TitledContainer>
    {/if}
</div>

<style lang="scss">
    .to-learn-page {
        max-width: 60em;
        margin: 0 auto;
        padding: 1em;
        // Line up digits in card labels so any numbers scan cleanly.
        font-variant-numeric: tabular-nums;
    }

    .groups.empty {
        color: var(--fg-subtle, #666);
        font-style: italic;
        padding: 0.75em 0;
        text-align: center;
    }

    .group {
        & + .group {
            margin-top: 1.1em;
            padding-top: 0.85em;
            border-top: 1px solid var(--border, #8884);
        }
    }

    .group-title {
        font-size: 1.05em;
        font-weight: 600;
        margin: 0 0 0.35em;

        .count {
            font-weight: 400;
            color: var(--fg-subtle, #666);
        }
    }

    .card-list {
        margin: 0;
        padding-inline-start: 1.4em;

        li {
            padding: 2px 0;
        }
    }
</style>
