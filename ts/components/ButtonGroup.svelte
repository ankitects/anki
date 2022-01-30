<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";
    import type {
        DefaultSlotInterface,
        GetSlotHostProps,
        SlotHostProps,
    } from "../sveltelib/dynamic-slotting";
    import contextProperty from "../sveltelib/context-property";

    export enum ButtonPosition {
        Standalone,
        InlineStart,
        Center,
        InlineEnd,
    }

    export interface ButtonSlotHostProps extends SlotHostProps {
        position: Writable<ButtonPosition>;
    }

    const key = Symbol("buttonGroup");
    const [context, setSlotHostContext] =
        contextProperty<GetSlotHostProps<ButtonSlotHostProps>>(key);

    export { context as slotHostContext };
</script>

<script lang="ts">
    import dynamicSlotting, { defaultInterface } from "../sveltelib/dynamic-slotting";
    import DynamicSlot from "./DynamicSlot.svelte";
    import ButtonGroupItem from "./ButtonGroupItem.svelte";
    import { writable } from "svelte/store";

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

    function makeButtonProps(): ButtonSlotHostProps {
        return {
            detach: writable(false),
            position: writable(ButtonPosition.Standalone),
        };
    }

    function updateButtonsProps(
        propsList: ButtonSlotHostProps[],
    ): ButtonSlotHostProps[] {
        for (const [index, props] of propsList.entries()) {
            const position = props.position;

            if (propsList.length === 1) {
                position.set(ButtonPosition.Standalone);
            } else if (index === 0) {
                position.set(ButtonPosition.InlineStart);
            } else if (index === propsList.length - 1) {
                position.set(ButtonPosition.InlineEnd);
            } else {
                position.set(ButtonPosition.Center);
            }
        }

        return propsList;
    }

    const { slotsInterface, resolveSlotContainer, dynamicSlotted } = dynamicSlotting(
        makeButtonProps,
        updateButtonsProps,
        setSlotHostContext,
        defaultInterface,
    );

    if (api) {
        Object.assign(api, slotsInterface);
    }
</script>

<div
    {id}
    class="button-group btn-group {className}"
    {style}
    dir="ltr"
    role="group"
    use:resolveSlotContainer
>
    <slot />
    <DynamicSlot slotHost={ButtonGroupItem} slotted={$dynamicSlotted} />
</div>

<style lang="scss">
    .button-group {
        display: flex;
        flex-flow: row var(--buttons-wrap);
    }
</style>
