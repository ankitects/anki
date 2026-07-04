<!-- Copyright: Ankitects Pty Ltd and contributors -->
<!-- License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html -->

<script lang="ts">
    import { tick } from "svelte";
    import type { ContextMenuMouseEvent } from "./types";

    let visible = $state(false);
    let x = $state(0);
    let y = $state(0);
    let contextMenuElement = $state<HTMLDivElement>();

    const { children } = $props();

    export async function show(event: ContextMenuMouseEvent) {
        event.preventDefault();

        x = event.clientX;
        y = event.clientY;
        visible = true;

        await tick();
        const rect = contextMenuElement!.getBoundingClientRect();
        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;

        if (x + rect.width > viewportWidth) {
            x = viewportWidth - rect.width;
        }
        if (y + rect.height > viewportHeight) {
            y = viewportHeight - rect.height;
        }

        x = Math.max(0, x);
        y = Math.max(0, y);
    }

    function hide() {
        visible = false;
    }

    function handleClickOutside(event: MouseEvent) {
        if (
            visible &&
            contextMenuElement &&
            !contextMenuElement.contains(event.target as Node)
        ) {
            hide();
        }
    }
</script>

<svelte:document on:click={handleClickOutside} />

{#if visible}
    <div
        bind:this={contextMenuElement}
        class="context-menu"
        style="left: {x}px; top: {y}px;"
        role="menu"
        tabindex="0"
        onclick={hide}
        onkeydown={hide}
    >
        {@render children?.()}
    </div>
{/if}

<style lang="scss">
    .context-menu {
        position: fixed;
        background: var(--canvas);
        border: 1px solid var(--border);
        border-radius: 4px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
        padding: 4px 0;
        min-width: 120px;
        z-index: 1000;
        font-size: 13px;
        outline: none;
    }
</style>
