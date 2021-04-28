<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string;
    let className = "";
    export { className as class };

    export let tooltip: string;

    let buttonRef: HTMLButtonElement;

    const nightMode = getContext(nightModeKey);

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<style lang="scss">
    @use 'ts/sass/button_mixins' as button;

    button {
        display: flex;
        justify-content: space-between;

        font-size: calc(var(--toolbar-size) / 2.3);
    }

    .btn-day {
        color: black;

        &:active {
            background-color: button.$focus-color;
            color: white;
        }
    }

    .btn-night {
        color: white;

        &:hover,
        &:focus {
            @include button.btn-night-base;
        }

        &:active {
            background-color: button.$focus-color;
            color: white;
        }
    }
</style>

<button
    {id}
    bind:this={buttonRef}
    class={`btn dropdown-item ${className}`}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    on:click
    on:mousedown|preventDefault>
    <slot />
</button>
