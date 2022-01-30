<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicSlot from "./DynamicSlot.svelte";
    import dynamicSlotting, {
        defaultProps,
        defaultInterface,
        setSlotHostContext,
    } from "../sveltelib/dynamic-slotting";
    import Item from "./Item.svelte";

    const { dynamicSlotted, slotsInterface } = dynamicSlotting(
        defaultProps,
        (v) => v,
        setSlotHostContext,
        defaultInterface,
    );

    export let api: Record<string, never> | undefined = undefined;

    if (api) {
        Object.assign(api, slotsInterface);
    }
</script>

<div class="section">
    <slot />
    <DynamicSlot slotHost={Item} slotted={$dynamicSlotted} />
</div>

<style lang="scss">
    .section {
        display: contents;
    }
</style>
