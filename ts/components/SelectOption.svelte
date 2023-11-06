<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { altPressed, isArrowDown, isArrowUp } from "@tslib/keys";
    import { getContext } from "svelte";
    import type { Writable } from "svelte/store";

    import { selectKey } from "./context-keys";
    import DropdownItem from "./DropdownItem.svelte";

    type T = $$Generic;

    export let selected = false;
    export let disabled = false;
    export let idx: number;
    export let id: string;
    export let value: T;
    export let selectFocus: (f: number) => void;
    export let setShow: (b: boolean) => void;

    export let element: HTMLButtonElement;

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
            focus(idx - 1);
        } else if (arrowDown) {
            focus(idx + 1);
        } else if (event.code === "Escape") {
            // TODO This doesn't work as the window typically catches the Escape as well
            // and closes the window; related to the problem in Select.svelte
            // - qt/aqt/browser/browser.py:377
            setShow(false);
        } else if (event.code === "Home") {
            focus(0);
        } else if (event.code === "End") {
            focus(Infinity);
        }
        if (event.code === "Tab") {
            // Tab actually should move DOM focus
            (element.parentElement?.nextElementSibling as HTMLElement).focus();
        }
    }

    function focus(idn: number) {
        selectFocus(idn);
    }

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
    on:keydown={onKeyDown}
    bind:buttonRef={element}
>
    <slot />
</DropdownItem>
