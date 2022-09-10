<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "./ButtonGroupItem.svelte";
    import DynamicallySlottable from "./DynamicallySlottable.svelte";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let api: Partial<Record<string, unknown>> = {};

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
</script>

<div {id} class="button-group btn-group {className}" {style} dir="ltr" role="group">
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
        <slot />
    </DynamicallySlottable>
</div>

<style lang="scss">
    .button-group {
        display: flex;
        flex-flow: row var(--buttons-wrap);
    }
</style>
