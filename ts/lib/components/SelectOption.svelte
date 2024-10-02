<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import type { Writable } from "svelte/store";

    import { selectKey } from "./context-keys";
    import DropdownItem from "./DropdownItem.svelte";

    type T = $$Generic;

    export let selected = false;
    export let disabled = false;
    export let id: string;
    export let value: T;

    export let element: HTMLButtonElement;

    const selectContext: Writable<{ value: T; setValue: Function }> =
        getContext(selectKey);
    const setValue = $selectContext.setValue;
</script>

<DropdownItem
    {disabled}
    {selected}
    id={selected ? id : undefined}
    active={value == $selectContext.value}
    role="option"
    on:click={() => setValue(value)}
    bind:buttonRef={element}
>
    <slot />
</DropdownItem>
