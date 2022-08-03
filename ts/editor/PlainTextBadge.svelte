<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";
    import { tweened } from "svelte/motion";
    import { cubicOut } from "svelte/easing";

    import { registerShortcut } from "../lib/shortcuts";
    import { context as editorFieldContext } from "./EditorField.svelte";

    const editorField = editorFieldContext.get();
    const keyCombination = "Control+Shift+X";
    const dispatch = createEventDispatcher();

    export let off = false;
    let hover = false;

    function toggle() {
        dispatch("toggle");
    }

    function shortcut(target: HTMLElement): () => void {
        return registerShortcut(toggle, keyCombination, { target });
    }

    onMount(() => editorField.element.then(shortcut));

    const point = tweened(0, {
        duration: 200,
        easing: cubicOut,
    });
    $: point.set(hover ? (off ? 18 : 2) : 10);
    $: base = off ? 9 : 11;
</script>

<div
    class="clickable"
    class:hover
    on:click|stopPropagation={toggle}
    on:mouseenter={() => (hover = true)}
    on:mouseleave={() => (hover = false)}
>
    <span class="plain-text-toggle" class:off class:on={!off}>
        <svg width="100%" viewBox="0 0 20 20">
            <polygon points="0,{base} 20,{base} 10,{$point}" />
        </svg>
    </span>
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
            top: -8px;
            width: 100%;
            height: 16px;
        }

        &.hover .plain-text-toggle {
            opacity: 1;
        }
    }
    .plain-text-toggle {
        opacity: 0;
        left: calc(50% - 10px);

        position: absolute;
        bottom: -11px;
        width: 16px;
        transition: opacity 0.2s ease-in;

        & svg {
            stroke: var(--border);

            stroke-width: 1px;
            & polygon {
                stroke-dasharray: 0 20 28.284;
            }
        }
        &.on {
            fill: var(--code-bg);
        }
        &.off {
            fill: var(--frame-bg);
        }
    }
    :global(.editor-field) {
        &:focus-within .plain-text-toggle.off svg {
            stroke-width: 2px;
            stroke: var(--focus-border);
        }
    }
</style>
