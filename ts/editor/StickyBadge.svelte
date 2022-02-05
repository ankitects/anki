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
    import { stickyOff, stickyOn } from "./icons";

    export let active: boolean;

    $: icon = active ? stickyOn : stickyOff;

    const editorField = editorFieldContext.get();
    const keyCombination = "F9";

    export let index: number;

    function toggle() {
        bridgeCommand(`toggleSticky:${index}`, (value: boolean) => {
            active = value;
        });
    }

    function shortcut(element: HTMLElement): void {
        registerShortcut(toggle, keyCombination, element);
    }

    onMount(() => editorField.element.then(shortcut));
</script>

<span class:highlighted={active} on:click|stopPropagation={toggle}>
    <Badge
        tooltip="{tr.editingToggleSticky()} ({getPlatformString(keyCombination)})"
        widthMultiplier={0.7}
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
