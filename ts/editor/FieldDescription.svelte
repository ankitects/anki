<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import type { Readable } from "svelte/store";

    import { directionKey, fontFamilyKey, fontSizeKey } from "../lib/context-keys";
    import { context } from "./EditingArea.svelte";

    const { content } = context.get();

    const fontFamily = getContext<Readable<string>>(fontFamilyKey);
    const fontSize = getContext<Readable<number>>(fontSizeKey);
    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);

    $: empty = $content.length === 0;
</script>

{#if empty}
    <div
        class="field-description"
        style:font-family={$fontFamily}
        style:font-size="{$fontSize}px"
        style:direction={$direction}
    >
        <slot />
    </div>
{/if}

<style>
    .field-description {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;

        opacity: 0.4;
        pointer-events: none;

        /* same as in ContentEditable */
        padding: 6px;

        /* stay a on single line */
        overflow-x: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
</style>
