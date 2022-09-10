<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";
    import { get, writable } from "svelte/store";

    import contextProperty from "../sveltelib/context-property";
    import type {
        GetSlotHostProps,
        SlotHostProps,
    } from "../sveltelib/dynamic-slotting";

    enum ButtonPosition {
        Standalone,
        InlineStart,
        Center,
        InlineEnd,
    }

    interface ButtonSlotHostProps extends SlotHostProps {
        position: Writable<ButtonPosition>;
    }

    const key = Symbol("buttonGroup");
    const [context, setSlotHostContext] =
        contextProperty<GetSlotHostProps<ButtonSlotHostProps>>(key);

    export { setSlotHostContext };

    export function createProps(): ButtonSlotHostProps {
        return {
            detach: writable(false),
            position: writable(ButtonPosition.Standalone),
        };
    }

    function nonDetached(props: ButtonSlotHostProps): boolean {
        return !get(props.detach);
    }

    export function updatePropsList(
        propsList: ButtonSlotHostProps[],
    ): ButtonSlotHostProps[] {
        const list = Array.from(propsList.filter(nonDetached).entries());

        for (const [index, props] of list) {
            const position = props.position;

            if (list.length === 1) {
                position.set(ButtonPosition.Standalone);
            } else if (index === 0) {
                position.set(ButtonPosition.InlineStart);
            } else if (index === list.length - 1) {
                position.set(ButtonPosition.InlineEnd);
            } else {
                position.set(ButtonPosition.Center);
            }
        }

        return propsList;
    }
</script>

<script lang="ts">
    export let id: string | undefined = undefined;
    export let hostProps: ButtonSlotHostProps | undefined = undefined;

    if (!context.available()) {
        console.log("ButtonGroupItem: should always have a slotHostContext");
    }

    const { detach, position } = hostProps ?? context.get().getProps();

    $: leftRadius =
        $position == ButtonPosition.Standalone ||
        $position == ButtonPosition.InlineStart
            ? "5px"
            : "0";
    $: rightRadius =
        $position == ButtonPosition.Standalone || $position == ButtonPosition.InlineEnd
            ? "5px"
            : "0";
    $: notFirst =
        $position == ButtonPosition.Center || $position == ButtonPosition.InlineEnd
            ? "-1px"
            : "0";
</script>

<div
    {id}
    class="button-group-item"
    class:not-first={notFirst}
    style:--border-left-radius={leftRadius}
    style:--border-right-radius={rightRadius}
>
    {#if !$detach}
        <slot />
    {/if}
</div>

<style lang="scss">
    .button-group-item {
        display: contents;
    }
    /* replace with gap once available */
    .not-first > :global(*) {
        margin-left: -1px;
    }
    :global([dir="rtl"]) .not-first > :global(*) {
        margin-right: -1px;
    }
</style>
