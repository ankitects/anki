<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import Badge from "../components/Badge.svelte";
    import * as tr from "../lib/ftl";
    import { getPlatformString, registerShortcut } from "../lib/shortcuts";
    import { context as editorFieldContext } from "./EditorField.svelte";
    import { plainTextIcon } from "./icons";

    const editorField = editorFieldContext.get();
    const keyCombination = "Control+Shift+X";
    const dispatch = createEventDispatcher();

    export let visible = false;
    export let off = false;

    function toggle() {
        dispatch("toggle");
    }

    function shortcut(target: HTMLElement): () => void {
        return registerShortcut(toggle, keyCombination, { target });
    }

    onMount(() => editorField.element.then(shortcut));
</script>

<span
    class="plain-text-badge"
    class:visible
    class:highlighted={!off}
    on:click|stopPropagation={toggle}
>
    <Badge
        tooltip="{tr.editingToggleHtmlEditor()} ({getPlatformString(keyCombination)})"
        iconSize={75}>{@html plainTextIcon}</Badge
    >
</span>

<style lang="scss">
    span {
        cursor: pointer;
        opacity: 0;

        &.visible {
            opacity: 0.4;
            &:hover {
                opacity: 0.8;
            }
        }
        &.highlighted {
            opacity: 1;
        }
    }
</style>
