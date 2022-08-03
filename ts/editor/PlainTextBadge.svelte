<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import { registerShortcut } from "../lib/shortcuts";
    import { context as editorFieldContext } from "./EditorField.svelte";

    const editorField = editorFieldContext.get();
    const keyCombination = "Control+Shift+X";
    const dispatch = createEventDispatcher();

    export let off = false;
    export let collapsed = false;

    function toggle() {
        dispatch("toggle");
    }

    function shortcut(target: HTMLElement): () => void {
        return registerShortcut(toggle, keyCombination, { target });
    }

    onMount(() => editorField.element.then(shortcut));
    let width = 0;
</script>

{#if !collapsed}
    <div class="clickable" style="--width: {width}px" on:click|stopPropagation={toggle}>
        <span
            class:off
            class:on={!off}
            class="plain-text-badge"
            class:highlighted={!off}
            bind:clientWidth={width}
        />
    </div>
{/if}

<style lang="scss">
    .clickable {
        position: relative;
        width: 100%;
        z-index: 3;
        cursor: pointer;
        // make whole division line clickable
        &::before {
            content: "";
            position: absolute;
            left: 0;
            top: -8px;
            width: 100%;
            height: 16px;
        }
    }
    .plain-text-badge {
        left: calc(50% - var(--width) / 2);

        position: absolute;
        opacity: 0;
        bottom: -4px;
        width: 4px;
        height: 4px;

        transform: rotate(-45deg);

        transition: width 0.2s ease-in-out, height 0.2s ease-in-out,
            opacity 0.2s ease-in, background-color 0s 0.2s;
        &.on {
            background: var(--code-bg);
            border-right: 1px solid var(--border);
            border-top: 1px solid var(--border);
        }
        &.off {
            background: var(--frame-bg);
            border-left: 1px solid var(--border);
            border-bottom: 1px solid var(--border);
        }
    }
    :global(.editor-field) {
        &:hover {
            & .plain-text-badge {
                opacity: 1;
                &.on,
                &.off {
                    width: 8px;
                    height: 8px;
                }
            }
        }
        &:focus-within .plain-text-badge.off {
            border-width: 2px;
            border-color: var(--focus-border);
        }
    }
</style>
