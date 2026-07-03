<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { renderMarkdown } from "@tslib/helpers";

    import Row from "./Row.svelte";
    import type { HelpItem } from "./types";
    import { mdiEarth } from "./icons";
    import Icon from "./Icon.svelte";

    export let item: HelpItem;
</script>

<Row>
    <h2>
        {#if item.url}
            {@html item.title}
        {:else}
            {@html item.title}
        {/if}
    </h2>
    {#if item.help}
        {#if item.global}
            <div class="icon">
                <Icon icon={mdiEarth} />
            </div>
        {/if}
        {@html renderMarkdown(item.help)}
    {:else}
        {@html renderMarkdown(
            tr.helpNoExplanation({
                link: "[GitHub](https://github.com/ankitects/anki)",
            }),
        )}
    {/if}
</Row>
{#if item.url}
    <hr />
    <div class="chapter-redirect">
        {@html renderMarkdown(
            tr.helpForMoreInfo({
                link: `<a href="${item.url}" title="${tr.helpOpenManualChapter({
                    name: item.title,
                })}">${item.title}</a>`,
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

    .icon {
        display: inline-block;
        width: 1em;
        fill: currentColor;
        margin-right: 0.25em;
        margin-bottom: 1.25em;
    }
</style>
