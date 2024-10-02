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

    export let flash: () => void;

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
    {:else}
        <Tag
            class={className}
            tagName={name}
            bind:flash
            bind:selected
            on:mousemove={setControlShift}
            on:click={onClick}
        >
            <span>{name}</span>
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
