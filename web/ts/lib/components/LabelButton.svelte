<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };
    export let primary = false;

    export let tooltip: string | undefined = undefined;
    export let active = false;
    export let disabled = false;
    export let tabbable = false;
    export let ellipsis = false;

    let buttonRef: HTMLButtonElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<button
    bind:this={buttonRef}
    {id}
    class="label-button {className}"
    class:active
    class:primary
    class:ellipsis
    title={tooltip}
    {disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault
>
    <slot />
</button>

<style lang="scss">
    @use "../sass/button-mixins" as button;

    .label-button {
        @include button.base($active-class: active);
        &.primary {
            @include button.base($primary: true);
        }
        @include button.border-radius;

        white-space: nowrap;
        &.ellipsis {
            overflow: hidden;
            text-overflow: ellipsis;
        }
        padding: 0 calc(var(--buttons-size) / 3);
        font-size: var(--font-size);
        width: auto;
        height: var(--buttons-size);
    }
</style>
