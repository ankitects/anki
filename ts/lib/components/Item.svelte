<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { SlotHostProps } from "$lib/sveltelib/dynamic-slotting";
    import { defaultSlotHostContext } from "$lib/sveltelib/dynamic-slotting";

    export let id: string | undefined = undefined;
    export let hostProps: SlotHostProps | undefined = undefined;

    if (!defaultSlotHostContext.available()) {
        console.log("Item: should always have a slotHostContext");
    }

    const { detach } = hostProps ?? defaultSlotHostContext.get().getProps();
</script>

<!-- div is necessary to preserve item position -->
<div class="item" {id}>
    {#if !$detach}
        <slot />
    {/if}
</div>

<style lang="scss">
    .item {
        display: contents;
    }
</style>
