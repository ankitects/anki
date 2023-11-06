<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { altPressed, isArrowDown, isArrowUp } from "@tslib/keys";
    import { createEventDispatcher, setContext } from "svelte";
    import { writable } from "svelte/store";

    import { selectKey } from "./context-keys";
    import IconConstrain from "./IconConstrain.svelte";
    import { chevronDown } from "./icons";
    import Popover from "./Popover.svelte";
    import WithFloating from "./WithFloating.svelte";
    import SelectOption from "./SelectOption.svelte";

    // eslint-disable
    type T = $$Generic;
    
    // * Moving in SelectOption
    // E may need to derive content, but we default to them being the same for convenience of usage
    type E = $$Generic;
    type C = $$Generic;
    export let list: E[];
    export let parser: (item: E) => {content: C, value?: T, disabled?: boolean} = (item) => {
        return {
            content: item as unknown as C,
        };
    };
    let options: HTMLButtonElement[] = Array(list.length);
    let selected: number = -1;
    const last = list.length - 1;
    const ids = {
        popover: "popover",
        focused: "focused",
    }

    export let id: string | undefined = undefined;

    let className = "";
    export { className as class };

    export let disabled = false;
    export let label = "<br>";
    export let value: T;

    const dispatch = createEventDispatcher();

    function setValue(v: T) {
        value = v;
        dispatch("change", { value });
    }

    function setShow(b: boolean) {
        showFloating = b;
        if (!showFloating) {
            element?.focus();
        }
    }

    export let element: HTMLElement | undefined = undefined;

    export let tooltip: string | undefined = undefined;

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";
    let hover = false;

    let showFloating = false;
    let clientWidth: number;

    const selectStore = writable({ value, setValue });
    $: $selectStore.value = value;
    setContext(selectKey, selectStore);

    function onKeyDown(event: KeyboardEvent) {
        // In accordance with ARIA APG combobox (https://www.w3.org/WAI/ARIA/apg/patterns/combobox/)
        const arrowDown = isArrowDown(event);
        const arrowUp = isArrowUp(event);
        const alt = altPressed(event);
        if ((arrowDown && alt) || event.code === "Enter" || event.code === "Space") {
            showFloating = true;
        } else if (arrowUp && alt) {
            showFloating = false;
        } else if (arrowDown || event.code === "Home") {
            showFloating = true;
            selected = 0;
        } else if (arrowUp || event.code === "End") {
            showFloating = true;
            selected = last;
        } else if (event.code === "Escape") {
            // TODO This doesn't work as the window typically catches the Escape as well
            // and closes the window; related to the problem in SelectOption.svelte
            // - qt/aqt/browser/browser.py:377
            showFloating = false;
        }
    }

    function revealed() {
        setTimeout(selectFocus, 0, selected);
    }

    /**
     * Focus on an option.
     * Negative values will clip to 0 and Infinity selects the last option
     * @param num index number to focus on
     * 
     * TODO Should only be changing visual focus, not DOM focus
     * https://www.w3.org/WAI/ARIA/apg/patterns/combobox/#keyboardinteraction
     */
    function selectFocus(num: number) {
        if (num < 0) {
            num = 0;
        } else if (num === Infinity) {
            num = last;
        }
        
        options[num].focus();
        selected = num;
    }
</script>

<WithFloating
    show={showFloating}
    offset={0}
    shift={0}
    hideArrow
    inline
    closeOnInsideClick
    keepOnKeyup
    on:close={() => (showFloating = false)}
    let:asReference
>
    <!-- TODO implement aria-label with semantic label -->
    <div
        {id}
        class="{className} select-container"
        class:rtl
        class:hover
        class:disabled
        title={tooltip}
        tabindex="0"
        role="combobox"
        aria-controls={ids.popover}
        aria-expanded={showFloating}
        aria-activedescendant={ids.focused}
        on:keydown={onKeyDown}
        on:mouseenter={() => (hover = true)}
        on:mouseleave={() => (hover = false)}
        on:click={() => (showFloating = !showFloating)}
        bind:this={element}
        use:asReference
        bind:clientWidth
    >
        <div class="inner">
            <div class="label">{@html label}</div>
        </div>
        <div class="chevron">
            <IconConstrain iconSize={80}>
                {@html chevronDown}
            </IconConstrain>
        </div>
    </div>
    <Popover
        slot="floating"
        scrollable
        --popover-width="{clientWidth}px"
        id={ids.popover}
        on:revealed={revealed}
    >
        {#each list.map(parser) as {content, value: optionValue, disabled}, idx (idx)}
            <SelectOption
                value={optionValue === undefined ? idx : optionValue}
                {idx}
                {disabled}
                {selectFocus}
                {setShow}
                selected={idx === selected}
                id={ids.focused}
                bind:element={options[idx]}
            >
                {content}
            </SelectOption>
        {/each}
    </Popover>
</WithFloating>

<style lang="scss">
    @use "sass/button-mixins" as button;

    $padding-inline: 0.5rem;

    .select-container {
        @include button.select($with-disabled: false);
        line-height: 1.5;
        height: 100%;
        position: relative;
        display: flex;
        flex-flow: row;
        justify-content: space-between;

        .inner {
            flex-grow: 1;
            position: relative;
            .label {
                position: absolute;
                top: 0;
                right: $padding-inline;
                bottom: 0;
                left: $padding-inline;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        }
    }

    .disabled {
        pointer-events: none;
        opacity: 0.5;
    }

    .chevron {
        height: 100%;
        align-self: flex-end;
        border-left: 1px solid var(--border-subtle);
    }

    :global([dir="rtl"]) {
        .chevron {
            border-left: none;
            border-right: 1px solid var(--border-subtle);
        }
    }
</style>
