<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import Badge from "../components/Badge.svelte";
    import { bridgeCommand } from "../lib/bridgecommand";
    import * as tr from "../lib/ftl";
    import { getPlatformString, registerShortcut } from "../lib/shortcuts";
    import { context as editorFieldContext } from "./EditorField.svelte";
    import { stickyIcon } from "./icons";

    export let active: boolean;
    export let visible: boolean;

    const editorField = editorFieldContext.get();
    const keyCombination = "F9";

    export let index: number;

    function toggle() {
        bridgeCommand(`toggleSticky:${index}`, (value: boolean) => {
            active = value;
        });
    }

    function shortcut(target: HTMLElement): () => void {
        return registerShortcut(toggle, keyCombination, { target });
    }

    onMount(() => editorField.element.then(shortcut));
</script>

<span class:highlighted={active} class:visible on:click|stopPropagation={toggle}>
    <Badge
        tooltip="{tr.editingToggleSticky()} ({getPlatformString(keyCombination)})"
        widthMultiplier={0.7}>{@html stickyIcon}</Badge
    >
</span>

<style lang="scss">
    span {
        cursor: pointer;
        opacity: 0;
        &.visible {
            transition: none;
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
