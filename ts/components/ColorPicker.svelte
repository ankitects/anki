<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;

    const nightMode = getContext(nightModeKey);

    function delegateToInput() {
        inputRef.click();
    }

    let buttonRef: HTMLButtonElement;
    let inputRef: HTMLInputElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef, input: inputRef }));
</script>

<button
    bind:this={buttonRef}
    tabindex="-1"
    {id}
    class={`btn ${className}`}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    on:click={delegateToInput}
    on:mousedown|preventDefault
>
    <input tabindex="-1" bind:this={inputRef} type="color" on:change />
</button>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    @import "ts/sass/bootstrap/functions";
    @import "ts/sass/bootstrap/variables";

    button {
        width: calc(var(--toolbar-size) - 0px);
        height: calc(var(--toolbar-size) - 0px);

        padding: 4px;
        overflow: hidden;

        border-top-left-radius: var(--border-left-radius);
        border-bottom-left-radius: var(--border-left-radius);

        border-top-right-radius: var(--border-right-radius);
        border-bottom-right-radius: var(--border-right-radius);
    }

    @include button.btn-day($with-disabled: false) using ($base) {
        @include button.rainbow($base);
    }

    @include button.btn-night($with-disabled: false) using ($base) {
        @include button.rainbow($base);
    }

    input {
        display: inline-block;
        cursor: pointer;
        opacity: 0;
    }
</style>
