<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { AscentScoresResponse } from "@generated/anki/stats_pb";

    import EstimateCell from "./EstimateCell.svelte";

    export let scores: AscentScoresResponse;

    $: level = scores.intervalPercent;
    $: unmappedPercent = scores.totalCards
        ? Math.round((scores.unmappedCards / scores.totalCards) * 100)
        : 0;
</script>

<div class="page">
    <header>
        <h1>Ascent</h1>
        <p class="lede">
            Three separate things, kept separate. What you can recall, how you do on
            questions you have not seen before, and how ready you are for the exam —
            which is the one we will not guess at.
        </p>
    </header>

    {#if scores.compoundingViolations.length}
        <!-- Uncertainty must compound upward. Anything here is a modelling bug,
             surfaced rather than clamped away. -->
        <section class="card violation">
            <h2>Modelling bug</h2>
            <p>
                A score came out more confident than the score it is built from. These
                numbers should not be trusted until this is fixed.
            </p>
            <ul>
                {#each scores.compoundingViolations as violation}
                    <li>{violation}</li>
                {/each}
            </ul>
        </section>
    {/if}

    <section class="card readiness">
        <div class="readiness-head">
            <h2>Readiness</h2>
            <span class="chip">Withheld</span>
        </div>
        {#if scores.readiness?.reportable}
            <p>Readiness is available.</p>
        {:else}
            <p class="headline">We will not show you a projected exam score.</p>
            <ol class="reasons">
                {#each scores.readiness?.reasons ?? [] as reason}
                    <li>{reason}</li>
                {/each}
            </ol>
            <p class="unlock">{scores.readiness?.unlock}</p>
        {/if}
    </section>

    <section class="card coverage">
        <h2>Your deck against the AAMC outline</h2>
        <div class="bar">
            <div
                class="mapped"
                style:width="{scores.totalCards
                    ? (scores.mappedCards / scores.totalCards) * 100
                    : 0}%"
            ></div>
        </div>
        <p>
            <strong>{scores.unmappedCards.toLocaleString()}</strong>
            of
            <strong>{scores.totalCards.toLocaleString()}</strong>
            cards ({unmappedPercent}%) could not be placed in an AAMC content category.
            They are excluded from every score below.
        </p>
        <p class="note">
            Cards are placed from their tags. A card whose tag names only a foundational
            concept is kept at that level rather than guessed into one of its categories
            — that is the finest grain AAMC publishes a weight for.
        </p>
    </section>

    {#if scores.topics.length}
        <table>
            <thead>
                <tr>
                    <th class="topic-col">Topic</th>
                    <th>
                        Memory
                        <span class="sub">what you can recall</span>
                    </th>
                    <th>
                        Performance
                        <span class="sub">questions you have not seen</span>
                    </th>
                </tr>
            </thead>
            <tbody>
                {#each scores.topics as topic}
                    <tr>
                        <td class="topic-col">
                            <div class="topic-id">
                                <span class="section">{topic.section}</span>
                                {topic.id}
                                {#if topic.grain === "foundational-concept"}
                                    <span class="grain">concept level</span>
                                {/if}
                            </div>
                            <div class="topic-title">{topic.title}</div>
                            <div class="counts">
                                {topic.studiedCards} of {topic.cards} cards studied ·
                                {topic.probes} probes
                            </div>
                        </td>
                        <td><EstimateCell estimate={topic.memory} {level} /></td>
                        <td><EstimateCell estimate={topic.performance} {level} /></td>
                    </tr>
                {/each}
            </tbody>
        </table>
    {:else}
        <section class="card">
            <h2>No topics yet</h2>
            <p>
                None of your cards carry an AAMC content-category tag, so there is
                nothing to score per topic. Tagging them is what turns the bucket above
                into topics here.
            </p>
        </section>
    {/if}

    <footer>
        <p>{scores.calibrationNote}</p>
        <p>
            Every range above is a {level}% range — the true value falls inside it about
            {level} times in 100. Performance starts from {scores.priorDescription}.
        </p>
    </footer>
</div>

<style lang="scss">
    .page {
        max-width: 60rem;
        margin: 0 auto;
        padding: 1.5rem 1.25rem 3rem;
        color: var(--fg);
    }

    h1 {
        font-size: 1.6rem;
        margin: 0 0 0.3rem;
    }

    h2 {
        font-size: 1.05rem;
        margin: 0 0 0.5rem;
    }

    .lede {
        color: var(--fg-subtle);
        max-width: 46rem;
        margin: 0;
    }

    .card {
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        background: var(--canvas-elevated);
        padding: 1rem 1.1rem;
        margin-top: 1.25rem;
    }

    .readiness-head {
        display: flex;
        align-items: center;
        gap: 0.6rem;
    }

    .chip {
        border: 1px solid var(--border-strong);
        border-radius: 999px;
        padding: 0.1rem 0.6rem;
        font-size: 0.75rem;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        color: var(--fg-subtle);
    }

    .headline {
        font-size: 1.15rem;
        margin: 0.4rem 0 0.6rem;
    }

    .reasons {
        margin: 0 0 0.75rem;
        padding-left: 1.2rem;
        color: var(--fg-subtle);

        li {
            margin-bottom: 0.5rem;
            max-width: 48rem;
        }
    }

    .unlock {
        margin: 0;
        padding-top: 0.6rem;
        border-top: 1px solid var(--border-subtle);
    }

    .violation {
        border-color: var(--border-strong);

        h2 {
            color: var(--fg);
        }
    }

    .bar {
        height: 10px;
        border-radius: 5px;
        background: var(--canvas-inset);
        border: 1px solid var(--border-subtle);
        overflow: hidden;
        margin-bottom: 0.7rem;
    }

    .mapped {
        height: 100%;
        background: var(--fg-subtle);
    }

    .note {
        color: var(--fg-subtle);
        font-size: 0.85em;
        margin-bottom: 0;
    }

    table {
        width: 100%;
        margin-top: 1.5rem;
        border-collapse: collapse;
    }

    th {
        text-align: left;
        font-weight: 600;
        font-size: 0.9rem;
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid var(--border);
        vertical-align: bottom;
    }

    .sub {
        display: block;
        font-weight: 400;
        font-size: 0.78rem;
        color: var(--fg-subtle);
    }

    td {
        padding: 0.8rem 0.75rem;
        border-bottom: 1px solid var(--border-subtle);
        vertical-align: top;
        width: 27%;
    }

    .topic-col {
        width: 46%;
    }

    .topic-id {
        font-weight: 600;
        font-variant-numeric: tabular-nums;
    }

    .section {
        display: inline-block;
        min-width: 2.6em;
        color: var(--fg-subtle);
        font-weight: 400;
    }

    .grain {
        font-weight: 400;
        font-size: 0.75em;
        color: var(--fg-subtle);
    }

    .topic-title {
        font-size: 0.85em;
        color: var(--fg-subtle);
        margin: 0.15em 0;
    }

    .counts {
        font-size: 0.78em;
        color: var(--fg-subtle);
        font-variant-numeric: tabular-nums;
    }

    footer {
        margin-top: 2rem;
        padding-top: 1rem;
        border-top: 1px solid var(--border-subtle);
        color: var(--fg-subtle);
        font-size: 0.85rem;

        p {
            margin: 0 0 0.4rem;
        }
    }

    :global(body) {
        background-color: var(--canvas);
    }
</style>
