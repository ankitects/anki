<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { IconData } from "./types";

    export let icon: IconData;
</script>

{#if import.meta.env}
    <svelte:component this={icon.component} />
{:else}
    {@html icon.url}
{/if}
