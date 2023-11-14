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
    import SelectOption from "./SelectOption.svelte";
    import WithFloating from "./WithFloating.svelte";

    // eslint-disable
    type T = $$Generic;

    // E may need to derive content, but we default to them being the same for convenience of usage
    type E = $$Generic;
    type C = $$Generic;
    export let list: E[];
    export let parser: (item: E) => { content: C; value?: T; disabled?: boolean } = (
        item,
    ) => {
        return {
            content: item as unknown as C,
        };
    };
    const parsed = list.map(parser).map(({ content, value, disabled = false }, i) => ({
        content,
        value: value === undefined ? (i as T) : value,
        disabled,
    }));
    const buttons: HTMLButtonElement[] = Array(list.length);
    let selected: number | null = null;
    const last = list.length - 1;
    const ids = {
        popover: "popover",
        focused: "focused",
    };

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
        if (arrowDown || arrowUp || event.code === "Space") {
            event.preventDefault();
        }

        if (selected === null || selected < 0) {
            if (
                (arrowDown && alt) ||
                event.code === "Enter" ||
                event.code === "Space"
            ) {
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
                // and closes the window
                // - qt/aqt/browser/browser.py:377
                showFloating = false;
            }
        } else {
            if (
                event.code === "Enter" ||
                event.code === "Space" ||
                event.code === "Tab" ||
                (arrowUp && altPressed(event))
            ) {
                setValue(parsed[selected].value);
                showFloating = false;
            } else if (arrowUp) {
                selectFocus(selected - 1);
            } else if (arrowDown) {
                selectFocus(selected + 1);
            } else if (event.code === "Escape") {
                // TODO This doesn't work as the window typically catches the Escape as well
                // and closes the window
                // - qt/aqt/browser/browser.py:377
                showFloating = false;
            } else if (event.code === "Home") {
                selectFocus(0);
            } else if (event.code === "End") {
                selectFocus(Infinity);
            }
        }
    }

    $: if (showFloating === false) {
        selected = null;
    }

    function revealed() {
        if (selected !== null) {
            setTimeout(selectFocus, 0, selected);
        }
    }

    /**
     * Focus on an option.
     * Values outside the range clip to either end
     * @param num index number to focus on
     */
    function selectFocus(num: number) {
        if (num < 0) {
            num = 0;
        } else if (num > last) {
            num = last;
        }

        if (selected !== null) {
            buttons[selected].classList.remove("focus");
        }
        buttons[num].classList.add("focus");
        buttons[num].scrollIntoView(false);
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
        {#each parsed as { content, value, disabled }, idx (idx)}
            <SelectOption
                {value}
                bind:element={buttons[idx]}
                {disabled}
                selected={idx === selected}
                id={ids.focused}
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
