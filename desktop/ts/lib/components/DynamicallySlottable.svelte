<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    /* import type { SlotHostProps } from "$lib/sveltelib/dynamic-slotting"; */
    import dynamicSlotting, {
        defaultInterface,
        defaultProps,
        setSlotHostContext as defaultContext,
    } from "$lib/sveltelib/dynamic-slotting";

    function id<T>(value: T): T {
        return value;
    }

    /**
     * This should be a Svelte component that accepts `id` and `hostProps`
     * as their props, only mounts a div with display:contents, and retrieves
     * its props via .getProps().
     * For a minimal example, have a look at `Item.svelte`.
     */
    export let slotHost: any; // typeof Item | typeof ButtonGroupItem;

    /**
     * We cannot properly type these right now.
     */
    export let createProps: any /* <T extends SlotHostProps>() => T */ =
        defaultProps as any;
    export let updatePropsList: any /* <T extends SlotHostProps>(list: T[]) => T[] */ =
        id;
    export let setSlotHostContext: any = defaultContext;
    export let createInterface = defaultInterface;

    const { slotsInterface, resolveSlotContainer, dynamicSlotted } = dynamicSlotting(
        createProps,
        updatePropsList,
        setSlotHostContext,
        createInterface,
    );

    export let api: Partial<Record<string, unknown>>;

    Object.assign(api, slotsInterface);
</script>

<div class="dynamically-slottable" use:resolveSlotContainer>
    <slot />

    {#each $dynamicSlotted as { component, hostProps } (component.id)}
        <svelte:component this={slotHost} id={component.id} {hostProps}>
            <svelte:component this={component.component} {...component.props} />
        </svelte:component>
    {/each}
</div>

<style lang="scss">
    .dynamically-slottable {
        display: contents;
    }
</style>
