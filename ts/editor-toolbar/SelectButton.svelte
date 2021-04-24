<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Readable } from "svelte/store";
    import type { Option } from "./SelectButton";
    import { onMount, createEventDispatcher, getContext } from "svelte";
    import { disabledKey } from "./contextKeys";
    import SelectOption from "./SelectOption.svelte";

    export let id: string;
    export let className = "";
    export let tooltip: string;

    function extendClassName(classes: string) {
        return `form-select ${classes}`;
    }

    export let disables = true;
    export let options: Option[];

    let buttonRef: HTMLSelectElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));

    const disabled = getContext<Readable<boolean>>(disabledKey);
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
    title={tooltip}>
    {#each options as option}
        <SelectOption {...option} />
    {/each}
</select>
