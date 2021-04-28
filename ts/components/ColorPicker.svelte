<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string;
    export let className = "";
    export let tooltip: string | undefined;

    const nightMode = getContext(nightModeKey);

    let buttonRef: HTMLButtonElement;
    let inputRef: HTMLInputElement;

    function delegateToInput() {
        inputRef.click();
    }

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<style lang="scss">
    @use "ts/sass/button_mixins" as button;

    @import "ts/sass/bootstrap/functions";
    @import "ts/sass/bootstrap/variables";

    button {
        padding: 0;

        width: calc(var(--toolbar-size) - 0px);
        height: calc(var(--toolbar-size) - 0px);

        padding: 4px;
        overflow: hidden;
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

<button
    bind:this={buttonRef}
    tabindex="-1"
    {id}
    class={`btn ${className}`}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    on:click={delegateToInput}
    on:mousedown|preventDefault>
    <input tabindex="-1" bind:this={inputRef} type="color" on:change />
</button>
