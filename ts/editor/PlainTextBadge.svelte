<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import * as tr from "../lib/ftl";
    import { getPlatformString, registerShortcut } from "../lib/shortcuts";
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
</script>

{#if !collapsed}
    <div class="clickable" on:click|stopPropagation={toggle}>
        <span
            class="plain-text-toggle"
            class:off
            class:on={!off}
            class:collapsed
            title="{tr.editingToggleHtmlEditor()} ({getPlatformString(keyCombination)})"
        >
            HTML
        </span>
    </div>
{/if}

<style lang="scss">
    .plain-text-toggle {
        opacity: 0;
        top: -6px;
        right: 8px;
        position: absolute;
        padding: 0 2px;
        font-size: xx-small;
        font-weight: bold;
        color: var(--border);

        transition: opacity 0.2s ease-in, color 0.2s ease-in;

        &::before {
            content: "";
            position: absolute;
            z-index: -1;
            top: 0;
            bottom: 0;
            left: 50%;
            right: 50%;

            transition: left 0.2s ease-in, right 0.2s ease-in;
        }
    }

    .plain-text-toggle.on {
        opacity: 1;
        color: var(--text-fg);
        &::before {
            right: 0px;
            left: -1px;

            background: linear-gradient(
                to bottom,
                var(--frame-bg) 50%,
                var(--code-bg) 0%
            );
        }
    }
    .plain-text-toggle.off::before {
        background: linear-gradient(
            to bottom,
            var(--frame-bg) 50%,
            var(--window-bg) 0%
        );
    }
    :global(.editor-field) {
        &:focus-within,
        &:hover {
            .plain-text-toggle {
                opacity: 1;

                &::before {
                    right: 0px;
                    left: -1px;
                }
            }
        }
    }

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
            height: 16px;
        }

        &:hover {
            .plain-text-toggle {
                color: var(--text-fg);
                opacity: 1;
            }
            .plain-text-toggle.on {
                color: var(--border);

                &::before {
                    left: 50%;
                    right: 50%;
                }
            }
        }
    }
</style>
