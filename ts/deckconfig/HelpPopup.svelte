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

<style>
    :global(svg) {
        vertical-align: text-bottom;
    }
</style>

<span
    href={'#'}
    bind:this={ref}
    data-toggle="tooltip"
    data-placement="bottom"
    title={html}>
    {@html infoCircle}
</span>
