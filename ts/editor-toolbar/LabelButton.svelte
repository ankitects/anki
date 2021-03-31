<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import type { Readable } from "svelte/store";
    import { disabledKey } from "./contextKeys";

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    export let label: string;
    export let onClick: (event: ClickEvent) => void;
    export let disables = true;

    let buttonRef: HTMLButtonElement;

    function extendClassName(className: string): string {
        return `${className} btn btn-secondary`;
    }

    const dispatch = createEventDispatcher();

    onMount(() => dispatch("mount", { button: buttonRef }));

    const disabledStore = getContext(disabledKey);
    $: disabled = disables && $disabledStore;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: auto;
        height: calc(28px + 2px);

        padding: 0 10px;

        border-radius: 0;
        border-color: var(--faint-border);

        &:focus {
            box-shadow: 0 0 12px 4px rgb(255 255 255 / 0.5);
        }

        &[disabled] {
            opacity: 0.4;
            cursor: not-allowed;

            border-color: var(--faint-border);
        }
    }
</style>

<button
    bind:this={buttonRef}
    {disabled}
    {id}
    class={extendClassName(className)}
    {...props}
    tabindex="-1"
    on:click={onClick}
    on:mousedown|preventDefault>
    {label}
</button>
