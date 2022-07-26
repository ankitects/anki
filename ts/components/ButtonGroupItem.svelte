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

    let leftRadius = 0;
    let rightRadius = 0;

    if (!context.available()) {
        console.log("ButtonGroupItem: should always have a slotHostContext");
    }

    const { detach, position } = hostProps ?? context.get().getProps();
    const radius = 5;

    function updateButtonStyle(position: ButtonPosition) {
        switch (position) {
            case ButtonPosition.Standalone:
                leftRadius = radius;
                rightRadius = radius;
                break;
            case ButtonPosition.InlineStart:
                leftRadius = radius;
                break;
            case ButtonPosition.Center:
                break;
            case ButtonPosition.InlineEnd:
                rightRadius = radius;
                break;
        }
    }

    $: updateButtonStyle($position);
</script>

<div
    class="button-group-item"
    {id}
    style:--border-left-radius="{leftRadius}px"
    style:--border-right-radius="{rightRadius}px"
>
    {#if !$detach}
        <slot />
    {/if}
</div>

<style lang="scss">
    .button-group-item {
        display: contents;
    }
</style>
