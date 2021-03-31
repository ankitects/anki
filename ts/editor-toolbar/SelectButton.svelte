<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import type { Readable } from "svelte/store";
    import { disabledKey } from "./contextKeys";
    import SelectOption from "./SelectOption.svelte";

    interface Option {
        label: string;
        value: string;
        selected: boolean;
    }

    export let id = "";
    export let className = "";
    export let props: Record<string, string> = {};

    function extendClassName(classes: string) {
        return `form-select ${classes}`;
    }

    export let disables;
    export let options: Option[];

    let buttonRef: HTMLSelectElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));

    const disabledStore = getContext(disabledKey);
    $: disabled = disables && $disabledStore;
</script>

<style lang="scss">
    select {
        display: inline-block;
        vertical-align: middle;

        height: var(--toolbar-size);
        width: auto;

        font-size: calc(var(--toolbar-size) / 2.3);
        user-select: none;
        box-shadow: none;
        border-radius: 0;

        &:hover {
            background-color: #eee;
        }

        &:focus {
            outline: none;
        }
    }
</style>

<select
    tabindex="-1"
    bind:this={buttonRef}
    {disabled}
    {id}
    class={extendClassName(className)}
    {...props}>
    {#each options as option}
        <SelectOption {...option} />
    {/each}
</select>
