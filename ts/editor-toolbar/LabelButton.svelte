<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { disabledKey } from "./contextKeys";

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    export let label: string;
    export let title: string;
    export let onClick: (event: MouseEvent) => void;
    export let disables = true;

    let buttonRef: HTMLButtonElement;

    function extendClassName(className: string): string {
        return `btn btn-secondary ${className}`;
    }

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));

    const disabled = getContext(disabledKey);
    $: _disabled = disables && $disabled;
</script>

<style lang="scss">
    button {
        display: inline-block;
        vertical-align: middle;
        width: auto;
        height: var(--toolbar-size);

        font-size: calc(var(--toolbar-size) / 2.3);
        padding: 0 calc(var(--toolbar-size) / 3);

        border-radius: 0;
        border-color: var(--faint-border);

        &:focus {
            box-shadow: 0 0 calc(var(--toolbar-size) / 2.5)
                calc(var(--toolbar-size) / 7.5) rgb(255 255 255 / 0.5);
        }

        &[disabled] {
            opacity: 0.4;
            cursor: not-allowed;

            border-color: var(--faint-border);
        }
    }
</style>

<button
    tabindex="-1"
    bind:this={buttonRef}
    disabled={_disabled}
    {id}
    class={extendClassName(className)}
    {...props}
    on:click={onClick}
    on:mousedown|preventDefault
    {title}>
    {label}
</button>
