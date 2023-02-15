<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@tslib/ftl";
    import { getPlatformString, registerShortcut } from "@tslib/shortcuts";
    import { onMount } from "svelte";

    import Badge from "../components/Badge.svelte";
    import { context as editorFieldContext } from "./EditorField.svelte";
    import { stickyIcon } from "./icons";

    const animated = !document.body.classList.contains("reduce-motion");

    export let active: boolean;
    export let show: boolean;

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

<span
    class:highlighted={active}
    class:visible={show || !animated}
    on:click|stopPropagation={toggle}
>
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
