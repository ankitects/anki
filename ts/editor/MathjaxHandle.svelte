<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import HandleSelection from "./HandleSelection.svelte";
    import HandleBackground from "./HandleBackground.svelte";
    import HandleControl from "./HandleControl.svelte";

    export let activeImage: HTMLImageElement | null = null;
    export let container: HTMLElement;
</script>

<HandleSelection {container} {activeImage}>
    {#if activeImage}
        <HandleBackground />

        <HandleControl active={false} />
    {/if}
</HandleSelection>
