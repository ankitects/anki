<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
  import { selectFocus } from "./helpers";

    import { altPressed, isArrowDown, isArrowUp } from "@tslib/keys";
    import { getContext } from "svelte";
    import type { Writable } from "svelte/store";

    import { selectKey, selectShowKey } from "./context-keys";
    import DropdownItem from "./DropdownItem.svelte";

    type T = $$Generic;

    export let disabled = false;
    export let value: T;

    let element: HTMLButtonElement;

    function onKeyDown(event: KeyboardEvent) {
        const arrowUp = isArrowUp(event);
        const arrowDown = isArrowDown(event);

        if (
            event.code === "Enter" ||
            event.code === "Space" ||
            event.code === "Tab" ||
            (arrowUp && altPressed(event))
        ) {
            setShow(false);
            setValue(value);
        } else if (arrowUp) {
            selectFocus(element?.previousElementSibling);
        } else if (arrowDown) {
            selectFocus(element?.nextElementSibling);
        } else if (event.code === "Escape") {
            // TODO This doesn't work as the window typically catches the Escape as well
            // and closes the window; related to the problem in Select.svelte
            // - qt/aqt/browser/browser.py:377
            setShow(false);
        } else if (event.code === "Home") {
            selectFocus(element.parentElement?.firstElementChild);
        } else if (event.code === "End") {
            selectFocus(element.parentElement?.lastElementChild);
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
