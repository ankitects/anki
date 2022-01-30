<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Item from "./Item.svelte";
    import DynamicSlot from "./DynamicSlot.svelte";
    import type { DefaultSlotInterface } from "../sveltelib/dynamic-slotting";
    import dynamicSlotting, {
        defaultProps,
        setSlotHostContext,
        defaultInterface,
    } from "../sveltelib/dynamic-slotting";
    import { pageTheme } from "../sveltelib/theme";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let size: number | undefined = undefined;
    export let wrap: boolean | undefined = undefined;

    $: buttonSize = size ? `--buttons-size: ${size}rem; ` : "";
    let buttonWrap: string;
    $: if (wrap === undefined) {
        buttonWrap = "";
    } else {
        buttonWrap = wrap ? `--buttons-wrap: wrap; ` : `--buttons-wrap: nowrap; `;
    }

    $: style = buttonSize + buttonWrap;

    export let api: Partial<DefaultSlotInterface> | undefined = undefined;

    const { slotsInterface, resolveSlotContainer, dynamicSlotted } = dynamicSlotting(
        defaultProps,
        (v) => v,
        setSlotHostContext,
        defaultInterface,
    );

    if (api) {
        Object.assign(api, slotsInterface);
    }
</script>

<div
    {id}
    class="button-toolbar btn-toolbar {className}"
    class:nightMode={$pageTheme.isDark}
    {style}
    role="toolbar"
    on:focusout
    use:resolveSlotContainer
>
    <slot />
    <DynamicSlot slotHost={Item} slotted={$dynamicSlotted} />
</div>

<style lang="scss">
    .button-toolbar {
        flex-wrap: var(--buttons-wrap);
        padding-left: 0.15rem;

        > :global(*) > :global(*) {
            /* TODO replace with gap once available */
            margin-right: 0.15rem;
            margin-bottom: 0.15rem;
        }
    }
</style>
