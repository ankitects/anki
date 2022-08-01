<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import type { Readable } from "svelte/store";

    import { directionKey, fontFamilyKey, fontSizeKey } from "../lib/context-keys";

    const fontFamily = getContext<Readable<string>>(fontFamilyKey);
    const fontSize = getContext<Readable<number>>(fontSizeKey);
    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
</script>
<div
    class="field-description"
    style:font-family={$fontFamily}
    style:font-size="{$fontSize}px"
    style:direction={$direction}
>
    <slot />
</div>

<style>
    .field-description {
        position: absolute;
        top: 0;
        left: 0;

        opacity: 0.4;
        cursor: text;
        /* same as in ContentEditable */
        padding: 6px;
        /* stay a on single line */
        max-width: 95%;
        overflow-x: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
</style>
