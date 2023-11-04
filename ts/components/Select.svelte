<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, setContext } from "svelte";
    import { writable } from "svelte/store";

    import { selectKey, selectShowKey } from "./context-keys";
    import IconConstrain from "./IconConstrain.svelte";
    import { chevronDown } from "./icons";
    import Popover from "./Popover.svelte";
    import WithFloating from "./WithFloating.svelte";
    import { altPressed, isArrowDown, isArrowUp, onSomeKey } from "@tslib/keys";

    // eslint-disable
    type T = $$Generic;

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
    }

    export let element: HTMLElement | undefined = undefined;

    export let tooltip: string | undefined = undefined;

    let children: HTMLDivElement;

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";
    let hover = false;

    let showFloating = false;
    let clientWidth: number;

    const selectStore = writable({ value, setValue });
    $: $selectStore.value = value;
    setContext(selectKey, selectStore);

    const selectShowStore = writable({ showFloating, setShow });
    $: $selectShowStore.showFloating = showFloating;
    setContext(selectShowKey, selectShowStore);

    function onKeyDown(event: KeyboardEvent) {
        // In accordance with ARIA APG combobox (https://www.w3.org/WAI/ARIA/apg/patterns/combobox/)
        let arrowDown = isArrowDown(event);
        let arrowUp = isArrowDown(event);
        let alt = altPressed(event);
        if (arrowDown && alt
            || event.code === "Enter"
            || event.code === "Space") {
            showFloating = true;
        } else if (arrowUp && alt) {
            showFloating = false;
        } else if (arrowDown || event.code === "Home") {
            showFloating = true;
            (children?.firstElementChild as HTMLElement).focus();
        } else if (arrowUp || event.code === "End") {
            showFloating = true;
            (children?.lastElementChild as HTMLElement).focus();
        } else if (event.code === "Escape") {
            showFloating = false;
        }
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
    <div
        {id}
        class="{className} select-container"
        class:rtl
        class:hover
        class:disabled
        title={tooltip}
        tabindex="0"
        role="button"
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
    <Popover bind:element={children} slot="floating" scrollable --popover-width="{clientWidth}px">
        <slot />
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
