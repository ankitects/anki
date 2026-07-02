<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";
    import { get, writable } from "svelte/store";

    import contextProperty from "$lib/sveltelib/context-property";
    import type {
        GetSlotHostProps,
        SlotHostProps,
    } from "$lib/sveltelib/dynamic-slotting";

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

    let style: string;

    if (!context.available()) {
        console.log("ButtonGroupItem: should always have a slotHostContext");
    }

    const { detach, position } = hostProps ?? context.get().getProps();
    const radius = "5px";

    function updateButtonStyle(position: ButtonPosition) {
        switch (position) {
            case ButtonPosition.Standalone:
                style = `--border-left-radius: ${radius}; --border-right-radius: ${radius}; `;
                break;
            case ButtonPosition.InlineStart:
                style = `--border-left-radius: ${radius}; --border-right-radius: 0; `;
                break;
            case ButtonPosition.Center:
                style = "--border-left-radius: 0; --border-right-radius: 0; ";
                break;
            case ButtonPosition.InlineEnd:
                style = `--border-left-radius: 0; --border-right-radius: ${radius}; `;
                break;
        }
    }

    $: updateButtonStyle($position);
</script>

<!-- div is necessary to preserve item position -->
<div class="button-group-item" {id} {style}>
    {#if !$detach}
        <slot />
    {/if}
</div>

<style lang="scss">
    .button-group-item {
        display: contents;
    }
</style>
