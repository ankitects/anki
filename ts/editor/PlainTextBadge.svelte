<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";

    import * as tr from "../lib/ftl";
    import { onMount } from "svelte";
    import { htmlOn, htmlOff } from "./icons";
    import { getEditorField } from "./EditorField.svelte";
    import { registerShortcut, getPlatformString } from "../lib/shortcuts";

    const editorField = getEditorField();
    const keyCombination = "Control+Shift+X";

    export let off = false;

    $: icon = off ? htmlOff : htmlOn;

    function toggle() {
        off = !off;
    }

    onMount(() =>
        registerShortcut(toggle, keyCombination, editorField.element as HTMLElement),
    );
</script>

<span on:click|stopPropagation={toggle}>
    <Badge
        tooltip="{tr.editingHtmlEditor()} ({getPlatformString(keyCombination)})"
        iconSize={80}>{@html icon}</Badge
    >
</span>

<style lang="scss">
    span {
        opacity: 0.6;

        &:hover {
            opacity: 1;
        }
    }
</style>
