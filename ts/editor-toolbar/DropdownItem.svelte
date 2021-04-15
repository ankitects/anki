<script lang="typescript">
    import { getContext } from "svelte";
    import { nightModeKey } from "./contextKeys";

    export let id: string;
    export let className = "";
    export let tooltip: string;

    export let onClick: (event: MouseEvent) => void;
    export let label: string;
    export let endLabel: string;

    const nightMode = getContext(nightModeKey);
</script>

<style lang="scss">
    @import "ts/bootstrap/functions";
    @import "ts/bootstrap/variables";

    button {
        display: flex;
        justify-content: space-between;

        color: black;

        &.nightMode {
            color: white;

            &:hover,
            &:focus {
                color: black;
            }

            &:active {
                color: white;
            }
        }

        &:focus {
            box-shadow: none;
        }
    }

    span {
        font-size: calc(var(--toolbar-size) / 2.3);
        color: inherit;
    }
</style>

<button
    {id}
    class={`btn dropdown-item ${className}`}
    class:nightMode
    title={tooltip}
    on:click={onClick}
    on:mousedown|preventDefault>
    <span class:me-2={endLabel}>{label}</span>
    {#if endLabel}<span>{endLabel}</span>{/if}
</button>
