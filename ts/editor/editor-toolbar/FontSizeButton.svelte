<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";
    import * as tr from "@generated/ftl";
    import { singleCallback } from "@tslib/typing";

    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";
    import { chevronDown } from "$lib/components/icons";

    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { surrounder } from "../rich-text-input";
    import type { FormattingNode, MatchType } from "$lib/domlib/surround";

    export let disabled = false;
    let showFloating = false;
    const inputSize = "";
    let currentInput = inputSize;

    const defaultSizes = [
        "10",
        "11",
        "12",
        "13",
        "14",
        "15",
        "16",
        "18",
        "20",
        "24",
        "30",
        "36",
        "48",
    ];

    $: if (disabled) {
        showFloating = false;
    }

    function transformSize(size: string): string {
        if (!size) {
            return "";
        }
        const s = size.trim().toLowerCase();
        if (/^\d+(\.\d+)?$/.test(s)) {
            return `${s}px`;
        }
        return s;
    }

    function toDisplayValue(size: string): string {
        if (!size) {
            return "";
        }
        return size.replace(/px$/i, "");
    }

    function updateCurrentSize() {
        const shadowRoot = document.activeElement?.shadowRoot;
        if (!shadowRoot) {
            return;
        }
        const selection = shadowRoot.getSelection();
        if (!selection || !selection.anchorNode) {
            return;
        }

        let node = selection.anchorNode;
        if (node.nodeType === Node.TEXT_NODE) {
            node = node.parentElement!;
        }

        if (node instanceof Element) {
            const computed = window.getComputedStyle(node).fontSize;
            currentInput = toDisplayValue(computed);
        }
    }

    // Surround Logic
    const key = "fontSize";
    // We use a module-level or component-level var to communicate with formatter.
    // Since formatter is defined inside component instance, it closes over this var.
    let sizePayload = "";

    function matcher(
        element: HTMLElement | SVGElement,
        match: MatchType<string>,
    ): void {
        const value = element.style.getPropertyValue("font-size");
        if (value.length > 0) {
            match.setCache(value);
        }
    }

    function merger(
        before: FormattingNode<string>,
        after: FormattingNode<string>,
    ): boolean {
        return before.getCache(sizePayload) === after.getCache(sizePayload);
    }

    function formatter(node: FormattingNode<string>): boolean {
        const size = transformSize(sizePayload);
        if (!size) {
            return false;
        }

        const extension = node.extensions.find(
            (element: HTMLElement | SVGElement): boolean => element.tagName === "SPAN",
        );

        if (extension) {
            extension.style.setProperty("font-size", size);
            return false;
        }

        const span = document.createElement("span");
        span.style.setProperty("font-size", size);
        node.range.toDOMRange().surroundContents(span);
        return true;
    }

    const format = { matcher, merger, formatter };

    // Register format
    const namedFormat = {
        key,
        name: "Font Size",
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();

    onMount(() => {
        removeFormats.update((formats) => [...formats, namedFormat]);
        return singleCallback(
            surrounder.active.subscribe((value) => {
                disabled = !value;
                if (value) {
                    updateCurrentSize();
                }
            }),
            surrounder.registerFormat(key, format),
        );
    });

    function applySize(size: string) {
        const cssValue = transformSize(size);
        if (!cssValue) {
            return;
        }

        currentInput = toDisplayValue(cssValue);
        sizePayload = cssValue;
        surrounder.overwriteSurround(key);
        showFloating = false;
    }

    function handleKey(e: KeyboardEvent) {
        if (e.key === "Enter") {
            applySize(currentInput);
        }
    }
</script>

<WithFloating
    show={showFloating}
    inline
    on:close={() => (showFloating = false)}
    let:asReference
>
    <!-- Combined Input + Dropdown Trigger -->
    <div class="font-size-button" use:asReference>
        <input
            type="text"
            bind:value={currentInput}
            on:keydown={handleKey}
            on:blur={() => applySize(currentInput)}
            {disabled}
            class="size-input"
        />
        <IconButton
            tooltip={tr.editingFontSize()}
            {disabled}
            on:click={() => (showFloating = !showFloating)}
        >
            <Icon icon={chevronDown} />
        </IconButton>
    </div>

    <Popover slot="floating">
        {#each defaultSizes as size}
            <DropdownItem on:click={() => applySize(size)}>
                {size}
            </DropdownItem>
        {/each}
    </Popover>
</WithFloating>

<style lang="scss">
    .font-size-button {
        display: flex;
        align-items: center;
        border: 1px solid var(--border-subtle);
        border-radius: var(--border-radius);
        background: var(--canvas-elevated); /* Match toolbar buttons */
        height: var(--axis-size); /* Standard button height */
        margin-right: 0.5rem;

        &:focus-within {
            border-color: var(--border-focus);
        }
    }

    .size-input {
        width: 3rem;
        border: none;
        background: transparent;
        color: var(--fg);
        text-align: center;
        font-size: var(--font-size-small); /* or inherit */
        padding: 0 4px;

        &:focus {
            outline: none;
        }
    }

    /* Override IconButton margins if necessary */
    :global(.font-size-button .icon-button) {
        padding: 0 2px;
        width: auto;
    }
</style>
