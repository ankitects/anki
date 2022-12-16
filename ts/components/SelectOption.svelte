<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import type { Writable } from "svelte/store";

    import { selectKey } from "./context-keys";
    import DropdownItem from "./DropdownItem.svelte";

    export let disabled = false;
    export let value: number;

    let element: HTMLButtonElement;

    function handleKey(e: KeyboardEvent) {
        /* Arrow key navigation */
        switch (e.code) {
            case "ArrowUp": {
                const prevSibling = element?.previousElementSibling as HTMLElement;
                if (prevSibling) {
                    prevSibling.focus();
                } else {
                    // close popover
                    document.body.click();
                }
                break;
            }
            case "ArrowDown": {
                const nextSibling = element?.nextElementSibling as HTMLElement;
                if (nextSibling) {
                    nextSibling.focus();
                } else {
                    // close popover
                    document.body.click();
                }
                break;
            }
        }
    }

    const selectContext: Writable<{ value: number; setValue: Function }> =
        getContext(selectKey);
    const setValue = $selectContext.setValue;
</script>

<DropdownItem
    {disabled}
    active={value == $selectContext.value}
    on:click={() => setValue(value)}
    on:keydown={handleKey}
    bind:buttonRef={element}
    tabbable
>
    <slot />
</DropdownItem>
