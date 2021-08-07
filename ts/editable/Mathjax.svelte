<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";
    import { convertMathjax } from "./mathjax";

    export let mathjax: string;
    export let block: boolean;
    /* have fixed fontSize for normal */
    export const fontSize: number = 20;

    const nightMode = getContext<boolean>(nightModeKey);

    $: [converted, title] = convertMathjax(mathjax, nightMode, fontSize);
    $: encoded = encodeURIComponent(converted);
</script>

<img
    src="data:image/svg+xml,{encoded}"
    class:block
    alt="Mathjax"
    {title}
    data-anki="mathjax"
    on:dragstart|preventDefault
/>

<style lang="scss">
    img {
        vertical-align: middle;

        &.block {
            display: block;
            margin: auto;
        }
    }
</style>
