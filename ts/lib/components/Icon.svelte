<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { IconData } from "./types";

    export let icon: IconData;

    let component: any = null;
    if (import.meta.env) {
        // @ts-expect-error internal property
        component = icon.component;
    }
</script>

{#if component}
    <svelte:component this={component} />
{:else}
    {@html icon.url}
{/if}
