<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Readable } from "svelte/store";
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;

    export let disables = true;

    const nightMode = getContext<boolean>(nightModeKey);
    const disabled = getContext<Readable<boolean>>(disabledKey);
    $: _disabled = disables && $disabled;

    let buttonRef: HTMLSelectElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    select {
        display: inline-block;
        vertical-align: middle;

        height: var(--toolbar-size);
        width: auto;

        font-size: calc(var(--toolbar-size) / 2.3);
        user-select: none;
        box-shadow: none;
        border-radius: 0;

        &:focus {
            outline: none;
        }
    }

    @include button.btn-day($with-hover: false);
    @include button.btn-night($with-hover: false);
</style>

<!-- svelte-ignore a11y-no-onchange -->

<select
    tabindex="-1"
    bind:this={buttonRef}
    disabled={_disabled}
    {id}
    class={className}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    on:change>
    <slot />
</select>
