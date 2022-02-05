<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import Badge from "../components/Badge.svelte";
    import * as tr from "../lib/ftl";
    import { getPlatformString, registerShortcut } from "../lib/shortcuts";
    import { context as editorFieldContext } from "./EditorField.svelte";
    import { htmlOff, htmlOn } from "./icons";

    const editorField = editorFieldContext.get();
    const keyCombination = "Control+Shift+X";

    export let off = false;

    $: icon = off ? htmlOff : htmlOn;

    function toggle() {
        off = !off;
    }

    function shortcut(element: HTMLElement): void {
        registerShortcut(toggle, keyCombination, element);
    }

    onMount(() => editorField.element.then(shortcut));
</script>

<span
    class="plain-text-badge"
    class:highlighted={!off}
    on:click|stopPropagation={toggle}
>
    <Badge
        tooltip="{tr.editingToggleHtmlEditor()} ({getPlatformString(keyCombination)})"
        iconSize={80}
        --icon-align="text-top">{@html icon}</Badge
    >
</span>

<style lang="scss">
    span {
        opacity: 0.4;

        &.highlighted {
            opacity: 1;
        }
        &:hover {
            opacity: 0.8;
        }
    }
</style>
