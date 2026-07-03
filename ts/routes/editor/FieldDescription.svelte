<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { directionKey, fontFamilyKey, fontSizeKey } from "@tslib/context-keys";
    import { getContext } from "svelte";
    import type { Readable } from "svelte/store";

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

        color: var(--fg-subtle);
        pointer-events: none;

        /* Stay a on single line */
        white-space: nowrap;
        text-overflow: ellipsis;

        /* The field description is placed absolutely on top of the editor field */
        /* So we need to make sure it does not escape the editor field if the */
        /* description is too long */
        overflow: hidden;
    }
</style>
