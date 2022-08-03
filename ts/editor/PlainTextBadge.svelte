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

    function toggle() {
        dispatch("toggle");
    }

    function shortcut(target: HTMLElement): () => void {
        return registerShortcut(toggle, keyCombination, { target });
    }

    onMount(() => editorField.element.then(shortcut));
    let width = 0;
</script>

<div class="clickable" style="--width: {width}px" on:click|stopPropagation={toggle}>
    <span
        class:off
        class:on={!off}
        class="plain-text-badge"
        class:highlighted={!off}
        bind:clientWidth={width}
    />
</div>

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
            top: -7px;
            width: 100%;
            height: 14px;
        }
    }
    .plain-text-badge {
        position: absolute;
        opacity: 0;
        width: 8px;
        height: 8px;
        transform: rotate(-45deg);
        left: calc(50% - var(--width) / 2);

        transition: bottom 0.2s ease-out;
        &.on {
            bottom: -10px;
            background: var(--code-bg);
            border-right: 1px solid var(--border);
            border-top: 1px solid var(--border);
        }
        &.off {
            bottom: 0px;
            background: var(--frame-bg);
            border-left: 1px solid var(--border);
            border-bottom: 1px solid var(--border);
        }
    }
    :global(.editor-field) {
        &:hover,
        &:focus-within {
            & .plain-text-badge {
                opacity: 1;
                transition: opacity 0.2s ease-in, bottom 0.2s ease-out;
                &.on,
                &.off {
                    bottom: -5px;
                }
            }
        }
        &:focus-within .plain-text-badge.off {
            border-width: 2px;
            border-color: var(--focus-border);
        }
    }
</style>
