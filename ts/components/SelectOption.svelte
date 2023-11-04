<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
  import { isArrowUp, isArrowDown, altPressed } from "@tslib/keys";

    import { getContext } from "svelte";
    import type { Writable } from "svelte/store";

    import { selectKey, selectShowKey } from "./context-keys";
    import DropdownItem from "./DropdownItem.svelte";

    type T = $$Generic;

    export let disabled = false;
    export let value: T;

    let element: HTMLButtonElement;

    function focus(el: HTMLElement) {
        // TODO Should only be changing visual focus, not DOM focus
        // https://www.w3.org/WAI/ARIA/apg/patterns/combobox/#keyboardinteraction
        el.focus();
    }

    function onKeyDown(event: KeyboardEvent) {
        let arrowUp = isArrowUp(event);
        let arrowDown = isArrowDown(event);

        if (event.code === "Enter" 
            || event.code === "Space" 
            || event.code === "Tab" 
            || arrowUp && altPressed(event))
        {
            setShow(false);
            setValue(value);
        } else if (arrowUp) {
            const prevSibling = element?.previousElementSibling as HTMLElement;
            focus(prevSibling);
        } else if (arrowDown) {
            const nextSibling = element?.nextElementSibling as HTMLElement;
            focus(nextSibling);
        } else if (event.code === "Escape") {
            setShow(false);
        } else if (event.code === "Home") {
            focus(element.parentElement?.firstElementChild as HTMLElement);
        } else if (event.code === "End") {
            focus(element.parentElement?.lastElementChild as HTMLElement);
        }
        if (event.code === "Tab") {
            // Tab actually should move DOM focus
            (element.parentElement?.nextElementSibling as HTMLElement).focus();
        }
    }

    const selectContext: Writable<{ value: T; setValue: Function }> =
        getContext(selectKey);
    const setValue = $selectContext.setValue;
    const selectShowContext: Writable<{ showFloating: boolean; setShow: Function }> =
        getContext(selectShowKey);
    const setShow = $selectShowContext.setShow;
</script>

<DropdownItem
    {disabled}
    active={value == $selectContext.value}
    on:click={() => setValue(value)}
    on:keydown={onKeyDown}
    bind:buttonRef={element}
    tabbable
>
    <slot />
</DropdownItem>
