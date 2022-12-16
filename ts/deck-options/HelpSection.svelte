<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { marked } from "marked";

    import Row from "../components/Row.svelte";
    import type { DeckOption } from "./types";

    export let section: DeckOption;
</script>

<Row>
    <h2>
        {#if section.url}
            {@html section.title}
        {:else}
            {@html section.title}
        {/if}
    </h2>
    {#if section.help}
        {@html marked(section.help)}
    {:else}
        {@html marked(
            tr.helpNoExplanation({
                link: "[GitHub](https://github.com/ankitects/anki)",
            }),
        )}
    {/if}
</Row>
{#if section.url}
    <hr />
    <div class="chapter-redirect">
        {@html marked(
            tr.helpForMoreInfo({
                link: `<a href="${section.url}" title="${tr.helpOpenManualChapter({
                    name: section.title,
                })}">${section.title}</a>`,
            }),
        )}
    </div>
{/if}

<style lang="scss">
    h2 {
        margin-bottom: 1em;
        width: 100%;
    }

    .chapter-redirect {
        width: 100%;
        color: var(--fg-subtle);
        font-size: small;
    }
</style>
