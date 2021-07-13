<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "./context-keys";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;
    export let disabled = false;

    const nightMode = getContext<boolean>(nightModeKey);

    let buttonRef: HTMLSelectElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<!-- svelte-ignore a11y-no-onchange -->

<select
    tabindex="-1"
    bind:this={buttonRef}
    {id}
    {disabled}
    class="{className} form-select"
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    class:visible-down-arrow={nightMode}
    title={tooltip}
    on:change
>
    <slot />
</select>

<style lang="scss">
    @use "ts/sass/button-mixins" as button;

    select {
        height: var(--buttons-size);
        /* Long option name can create overflow */
        overflow-x: hidden;
    }

    .visible-down-arrow {
        /* override the default down arrow */
        background-image: button.down-arrow(white);
    }

    @include button.btn-day($with-hover: false);
    @include button.btn-night($with-hover: false);
</style>
