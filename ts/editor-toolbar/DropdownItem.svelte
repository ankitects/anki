<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
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
    @use 'ts/sass/button_mixins' as button;

    button {
        display: flex;
        justify-content: space-between;

        &:active {
            background-color: button.$focus-color;
            color: white;
        }
    }

    .btn-day {
        color: black;
    }

    .btn-night {
        color: white;

        &:hover,
        &:focus {
            @include button.btn-night-base;
        }
    }

    span {
        font-size: calc(var(--toolbar-size) / 2.3);
        color: inherit;
    }

    .monospace {
        font-family: monospace;
    }
</style>

<button
    {id}
    class={`btn dropdown-item ${className}`}
    class:btn-day={!nightMode}
    class:btn-night={nightMode}
    title={tooltip}
    on:click={onClick}
    on:mousedown|preventDefault>
    <span class:me-3={endLabel}>{label}</span>
    {#if endLabel}<span class="monospace">{endLabel}</span>{/if}
</button>
