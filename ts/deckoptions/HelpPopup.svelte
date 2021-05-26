<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { infoCircle } from "./icons";
    import { onMount } from "svelte";
    import Tooltip from "bootstrap/js/dist/tooltip";

    export let html: string;

    let ref: HTMLAnchorElement;

    onMount(() => {
        new Tooltip(ref, {
            placement: "bottom",
            html: true,
            offset: [0, 20],
        });
    });
</script>

<span bind:this={ref} title={html}>
    {@html infoCircle}
</span>

<style>
    span :global(svg) {
        vertical-align: text-bottom;
        opacity: 0.3;
    }
</style>
