<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Badge from "../components/Badge.svelte";
    import { onMount } from "svelte";
    import { stickyOn, stickyOff } from "./icons";
    import { getContext, editorFieldKey } from "./context";
    import * as tr from "../lib/ftl";
    import { bridgeCommand } from "../lib/bridgecommand";
    import { registerShortcut, getPlatformString } from "../lib/shortcuts";

    export let active: boolean;

    $: icon = active ? stickyOn : stickyOff;

    const editorField = getContext(editorFieldKey);
    const keyCombination = "F9";

    export let index: number;

    function toggleSticky() {
        bridgeCommand(`toggleSticky:${index}`, (value: boolean) => {
            active = value;
        });
    }

    onMount(() => registerShortcut(toggleSticky, keyCombination, editorField.element));
</script>

<span on:click|stopPropagation={toggleSticky}>
    <Badge
        tooltip="{tr.editingToggleSticky()} ({getPlatformString(keyCombination)})"
        widthMultiplier={0.7}>{@html icon}</Badge
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
