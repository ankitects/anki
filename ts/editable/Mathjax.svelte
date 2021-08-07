<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { convertMathjax } from "./mathjax";

    export let mathjax: string;
    export let block: boolean;

    $: converted = convertMathjax(mathjax);
    $: encoded = encodeURIComponent(converted);
</script>

<img
    src="data:image/svg+xml,{encoded}"
    class:block
    alt="Mathjax"
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
