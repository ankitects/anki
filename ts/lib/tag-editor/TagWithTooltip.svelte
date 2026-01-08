<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { controlPressed, shiftPressed } from "@tslib/keys";
    import { createEventDispatcher } from "svelte";

    import WithTooltip from "$lib/components/WithTooltip.svelte";
    import { pageTheme } from "$lib/sveltelib/theme";

    import Tag from "./Tag.svelte";
    import { delimChar } from "./tags";

    export let name: string;
    let className: string = "";
    export { className as class };

    export let tooltip: string;

    export let selected: boolean;
    export let active: boolean;
    export let shorten: boolean;
    export let truncateMiddle: boolean = false;
    export let editorWidth: number = 0;

    export let flash: () => void;

    let displayName = name;
    let needsTooltip = false;
    let tagButton: HTMLButtonElement | null = null;
    let cachedFont: string | undefined;
    let cachedEllipsisWidth: number | undefined;

    const ELLIPSIS = "…";
    const PADDING_PX = 60; // Account for padding, delete badge, etc.

    // Canvas for text measurement - no DOM thrashing
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d")!;

    function getTextWidth(text: string): number {
        return ctx.measureText(text).width;
    }

    function fitText(): void {
        if (!truncateMiddle || !tagButton || editorWidth <= 0) {
            displayName = name;
            needsTooltip = false;
            return;
        }

        // Keep font correct (can change via theme/class/parent)
        const font = getComputedStyle(tagButton).font;
        if (cachedFont !== font) {
            cachedFont = font;
            cachedEllipsisWidth = undefined;
        }
        ctx.font = cachedFont;

        const maxW = editorWidth - PADDING_PX;
        if (maxW <= 0) {
            displayName = ELLIPSIS;
            needsTooltip = true;
            return;
        }

        const fullWidth = getTextWidth(name);
        if (fullWidth <= maxW) {
            displayName = name;
            needsTooltip = false;
            return;
        }

        const ellipsisWidth =
            cachedEllipsisWidth ?? (cachedEllipsisWidth = getTextWidth(ELLIPSIS));

        if (ellipsisWidth > maxW) {
            displayName = ELLIPSIS;
            needsTooltip = true;
            return;
        }

        const build = (k: number): string => {
            const kk = Math.max(0, Math.min(k, name.length));
            const head = Math.ceil(kk / 2);
            const tail = kk - head;
            return (
                name.slice(0, head) +
                ELLIPSIS +
                (tail > 0 ? name.slice(name.length - tail) : "")
            );
        };

        // Find maximum k such that build(k) fits in maxW
        let lo = 0;
        let hi = name.length;
        let bestK = 0;

        while (lo <= hi) {
            const mid = (lo + hi) >> 1;
            const candidate = build(mid);
            if (getTextWidth(candidate) <= maxW) {
                bestK = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        displayName = build(bestK);
        needsTooltip = true;
    }

    function onTagMount(event: CustomEvent<{ button: HTMLButtonElement }>): void {
        tagButton = event.detail.button;
        cachedFont = undefined; // Reset to get fresh font on mount
        cachedEllipsisWidth = undefined;
        fitText();
    }

    // Re-fit when name, truncateMiddle, or editorWidth changes
    $: {
        name;
        truncateMiddle;
        editorWidth;
        fitText();
    }

    const dispatch = createEventDispatcher();

    let control = false;
    let shift = false;

    $: selectMode = control || shift;

    function setControlShift(event: KeyboardEvent | MouseEvent): void {
        control = controlPressed(event);
        shift = shiftPressed(event);
    }

    function onClick(): void {
        if (shift) {
            dispatch("tagrange");
        } else if (control) {
            dispatch("tagselect");
        } else {
            dispatch("tagclick");
        }
    }

    function processTagName(name: string): string {
        const parts = name.split(delimChar);

        if (parts.length === 1) {
            return name;
        }

        return `…${delimChar}` + parts[parts.length - 1];
    }

    function hasMultipleParts(name: string): boolean {
        return name.split(delimChar).length > 1;
    }
    const hoverClass = "tag-icon-hover";
</script>

<svelte:body on:keydown={setControlShift} on:keyup={setControlShift} />

<div
    class:select-mode={selectMode}
    class:night-mode={$pageTheme.isDark}
    class:empty={name === ""}
>
    {#if active}
        <Tag
            class={className}
            tagName={name}
            on:mousemove={setControlShift}
            on:click={onClick}
        >
            {name}
            <slot {selectMode} {hoverClass} />
        </Tag>
    {:else if shorten && hasMultipleParts(name)}
        <WithTooltip {tooltip} trigger="hover" placement="top" let:createTooltip>
            <Tag
                class={className}
                tagName={name}
                bind:flash
                bind:selected
                on:mousemove={setControlShift}
                on:click={onClick}
                on:mount={(event) => createTooltip(event.detail.button)}
            >
                <span>{processTagName(name)}</span>
                <slot {selectMode} {hoverClass} />
            </Tag>
        </WithTooltip>
    {:else if needsTooltip}
        <WithTooltip tooltip={name} trigger="hover" placement="top" let:createTooltip>
            <Tag
                class={className}
                tagName={name}
                bind:flash
                bind:selected
                on:mousemove={setControlShift}
                on:click={onClick}
                on:mount={(event) => {
                    createTooltip(event.detail.button);
                    onTagMount(event);
                }}
            >
                <span>{displayName}</span>
                <slot {selectMode} {hoverClass} />
            </Tag>
        </WithTooltip>
    {:else}
        <Tag
            class={className}
            tagName={name}
            bind:flash
            bind:selected
            on:mousemove={setControlShift}
            on:click={onClick}
            on:mount={onTagMount}
        >
            <span>{displayName}</span>
            <slot {selectMode} {hoverClass} />
        </Tag>
    {/if}
</div>

<style lang="scss">
    .select-mode :global(button:hover) {
        display: contents;
        cursor: crosshair;

        :global(.tag-icon-hover) {
            opacity: 0;
        }
    }

    :global(.tag-icon-hover svg:hover) {
        border-radius: 5px;

        $white-translucent: rgb(255 255 255 / 0.35);
        $dark-translucent: rgb(0 0 0 / 0.1);

        background-color: $dark-translucent;

        .night-mode & {
            background-color: $white-translucent;
        }
    }

    .empty {
        visibility: hidden;
    }
</style>
