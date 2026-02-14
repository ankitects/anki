<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { formatLineSpacingIcon } from "$lib/components/icons";
    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";

    export let disabled = false;

    let showFloating = false;
    $: if (disabled) {
        showFloating = false;
    }

    const options = [
        { value: "1", label: "1.0" },
        { value: "1.15", label: "1.15" },
        { value: "1.5", label: "1.5" },
        { value: "2", label: "2.0" },
    ];

    function setLineHeight(height: string) {
        const shadowRoot = document.activeElement?.shadowRoot;
        if (!shadowRoot) {
            return;
        }

        const selection = shadowRoot.getSelection();
        if (!selection || selection.rangeCount === 0) {
            return;
        }

        const range = selection.getRangeAt(0);
        let node = range.commonAncestorContainer;
        if (node.nodeType === Node.TEXT_NODE) {
            node = node.parentElement!;
        }

        // Find closest block
        while (node && node !== shadowRoot) {
            if (node instanceof HTMLElement) {
                const tagName = node.tagName;
                if (
                    tagName === "DIV" ||
                    tagName === "P" ||
                    tagName === "LI" ||
                    tagName === "H1" ||
                    tagName === "H2" ||
                    tagName === "H3" ||
                    tagName === "H4" ||
                    tagName === "H5" ||
                    tagName === "H6"
                ) {
                    node.style.lineHeight = height;
                    showFloating = false;
                    return;
                }
            }
            node = node.parentNode!;
        }

        // If no block found, we might be at root. Wrap in div?
        document.execCommand("formatBlock", false, "div");
        // Try again finding the block
        // Re-get selection as it might have changed
        setLineHeight(height);
    }
</script>

<WithFloating
    show={showFloating}
    inline
    on:close={() => (showFloating = false)}
    let:asReference
>
    <span class="line-height-button" use:asReference>
        <IconButton
            tooltip={tr.editingLineHeight()}
            {disabled}
            on:click={() => (showFloating = !showFloating)}
        >
            <Icon icon={formatLineSpacingIcon} />
        </IconButton>
    </span>

    <Popover slot="floating">
        {#each options as option}
            <DropdownItem on:click={() => setLineHeight(option.value)}>
                {option.label}
            </DropdownItem>
        {/each}
    </Popover>
</WithFloating>

<style lang="scss">
    .line-height-button {
        line-height: 1;
    }
</style>
