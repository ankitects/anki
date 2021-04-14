<script lang="typescript">
    import { getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string;
    export let className = "";
    export let tooltip: string;

    export let onChange: (event: Event) => void;

    function extendClassName(className: string): string {
        return `btn ${className}`;
    }

    const nightMode = getContext(nightModeKey);
</script>

<style lang="scss">
    button {
        padding: 0;
    }

    input {
        display: inline-block;
        opacity: 0;

        width: calc(var(--toolbar-size) - 2px);
        height: calc(var(--toolbar-size) - 7px);
    }
</style>

<button
    tabindex="-1"
    {id}
    class={extendClassName(className)}
    class:btn-light={!nightMode}
    class:btn-secondary={nightMode}
    title={tooltip}
    on:mousedown|preventDefault>
    <span> <input type="color" on:change={onChange} /> </span>
</button>
