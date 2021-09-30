<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, getContext, createEventDispatcher } from "svelte";
    import { nightModeKey } from "../components/context-keys";

    let className: string = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;
    export let selected: boolean = false;

    const dispatch = createEventDispatcher();

    let flashing: boolean = false;

    export function flash(): void {
        flashing = true;
        setTimeout(() => (flashing = false), 300);
    }

    const nightMode = getContext<boolean>(nightModeKey);

    let button: HTMLButtonElement;

    onMount(() => dispatch("mount", { button }));
</script>

<button
    bind:this={button}
    class="tag btn d-inline-flex align-items-center text-nowrap ps-2 pe-1 {className}"
    class:selected
    class:flashing
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    tabindex="-1"
    title={tooltip}
    on:mousemove
    on:click
>
    <slot />
</button>

<style lang="scss">
    @use "button-mixins" as button;

    @keyframes flash {
        0% {
            filter: invert(0);
        }
        50% {
            filter: invert(0.4);
        }
        100% {
            filter: invert(0);
        }
    }

    button {
        padding-top: 0.1rem;
        padding-bottom: 0.1rem;

        &:focus,
        &:active {
            outline: none;
            box-shadow: none;
        }

        &.tag {
            --border-color: var(--medium-border);

            border: 1px solid var(--border-color) !important;
            border-radius: 5px;
        }

        &.flashing {
            animation: flash 0.3s linear;
        }

        &.selected {
            box-shadow: 0 0 0 2px var(--focus-shadow);
            --border-color: var(--focus-border);
        }
    }

    @include button.btn-day(
        $with-active: false,
        $with-disabled: false,
        $with-hover: false
    );

    @include button.btn-night(
        $with-active: false,
        $with-disabled: false,
        $with-hover: false
    );
</style>
