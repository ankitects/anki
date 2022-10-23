<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { marked } from "marked";

    import Row from "../components/Row.svelte";
    import * as tr from "../lib/ftl";
    import type { DeckOption } from "./types";

    export let section: DeckOption;
</script>

<Row>
    <h2>
        {#if section.url}
            <a
                href={section.url}
                title={tr.helpOpenManualChapter({ name: section.title })}
            >
                {@html section.title}
            </a>
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
    <Row>
        {@html marked(
            tr.helpForMoreInfo({
                link: `<a href="${section.url}" title="${tr.helpOpenManualChapter({
                    name: section.title,
                })}">${section.title}</a>`,
            }),
        )}
    </Row>
{/if}

<style lang="scss">
    h2 {
        margin-bottom: 1em;
        a {
            cursor: pointer;
            border-bottom: 1px solid var(--border);
            text-decoration: none;
            color: var(--fg);
            &:hover {
                border-color: var(--fg);
            }
        }
    }
</style>
