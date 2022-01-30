<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { SvelteComponent } from "svelte";
    import dynamicSlotting, { defaultProps, defaultInterface, setSlotHostContext as defaultContext } from "../sveltelib/dynamic-slotting";

    function id<T>(value: T): T {
        return value;
    }

    export let slotHost: typeof SvelteComponent;
    export let createProps = defaultProps;
    export let updatePropsList = id;
    export let setSlotHostContext = defaultContext;
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
