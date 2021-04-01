<script lang="typescript">
    import { onMount, createEventDispatcher, getContext } from "svelte";
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
    export let title: string;

    function extendClassName(classes: string) {
        return `form-select ${classes}`;
    }

    export let disables = true;
    export let options: Option[];

    let buttonRef: HTMLSelectElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));

    const disabled = getContext(disabledKey);
    $: _disabled = disables && $disabled;
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
    disabled={_disabled}
    {id}
    class={extendClassName(className)}
    {...props}
    {title}>
    {#each options as option}
        <SelectOption {...option} />
    {/each}
</select>
