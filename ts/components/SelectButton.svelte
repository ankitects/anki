<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Readable } from "svelte/store";
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { disabledKey } from "./contextKeys";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;

    export let disables = true;

    let buttonRef: HTMLSelectElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));

    const disabled = getContext<Readable<boolean>>(disabledKey);
    $: _disabled = disables && $disabled;
</script>

<style lang="scss">
    select {
        display: inline-block;
        vertical-align: middle;

        height: var(--toolbar-size);
        width: auto;

        font-size: calc(var(--toolbar-size) / 2.3);
        user-select: none;
        box-shadow: none;
        border-radius: 0;

        &:hover {
            background-color: #eee;
        }

        &:focus {
            outline: none;
        }
    }
</style>

<!-- svelte-ignore a11y-no-onchange -->

<select
    tabindex="-1"
    bind:this={buttonRef}
    disabled={_disabled}
    {id}
    class={` ${className}`}
    title={tooltip}
    on:change>
    <slot />
</select>
