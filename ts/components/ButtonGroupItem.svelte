<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        slotHostContext,
        ButtonSlotHostProps,
        ButtonPosition,
    } from "./ButtonGroup.svelte";
    import Detachable from "./Detachable.svelte";

    export let id: string | undefined = undefined;
    export let hostProps: ButtonSlotHostProps | undefined = undefined;

    let style: string;

    const radius = "5px";

    const leftStyle = `--border-left-radius: ${radius}; --border-right-radius: 0; `;
    const rightStyle = `--border-left-radius: 0; --border-right-radius: ${radius}; `;

    if (!slotHostContext.available()) {
        console.log("ButtonGroupItem: should always have a slotHostContext");
    }

    const { detach, position } = hostProps ?? slotHostContext.get().getProps();

    function updateButtonStyle(position: ButtonPosition) {
        switch (position) {
            case ButtonPosition.Standalone:
                style = `--border-left-radius: ${radius}; --border-right-radius: ${radius}; `;
                break;
            case ButtonPosition.InlineStart:
                style = leftStyle;
                break;
            case ButtonPosition.Center:
                style = "--border-left-radius: 0; --border-right-radius: 0; ";
                break;
            case ButtonPosition.InlineEnd:
                style = rightStyle;
                break;
        }
    }

    $: updateButtonStyle($position);
</script>

<!-- div is necessary to preserve item position -->
<div {id} class="button-group-item" {style}>
    <Detachable detached={$detach}>
        <slot />
    </Detachable>
</div>

<style lang="scss">
    .button-group-item {
        display: contents;
    }
</style>
