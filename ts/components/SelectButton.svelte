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

<!-- svelte-ignore a11y-no-onchange -->

<select
    tabindex="-1"
    bind:this={buttonRef}
    disabled={_disabled}
    {id}
    class="{className} form-select"
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    on:change
>
    <slot />
</select>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    select {
        height: var(--buttons-size);
        /* Long option name can create overflow */
        overflow-x: hidden;
    }

    .btn-night {
        /* override the default down arrow */
        background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3e%3cpath fill='none' stroke='%23FFFFFF' stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M2 5l6 6 6-6'/%3e%3c/svg%3e");
    }

    @include button.btn-day($with-hover: false);
    @include button.btn-night($with-hover: false);
</style>
