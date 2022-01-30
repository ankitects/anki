<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { SvelteComponent } from "svelte";
    import type { DynamicSlotted } from "../sveltelib/dynamic-slotting";

    export let slotHost: typeof SvelteComponent;
    export let slotted: DynamicSlotted[];
</script>

{#each slotted as { component, hostProps } (component.id)}
    <svelte:component this={slotHost} id={component.id} {hostProps}>
        <svelte:component this={component.component} {...component.props} />
    </svelte:component>
{/each}
