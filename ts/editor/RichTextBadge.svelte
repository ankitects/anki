<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString, registerShortcut } from "@tslib/shortcuts";
    import { createEventDispatcher, onDestroy } from "svelte";

    import Badge from "../components/Badge.svelte";
    import { context as editorFieldContext } from "./EditorField.svelte";
    import { richTextIcon } from "./icons";

    const animated = !document.body.classList.contains("reduce-motion");

    const editorField = editorFieldContext.get();
    const keyCombination = "Control+Shift+X";
    const dispatch = createEventDispatcher();

    export let show = false;
    export let off = false;

    function toggle() {
        dispatch("toggle");
    }

    let unregister: ReturnType<typeof registerShortcut> | undefined;

    editorField.element.then((target) => {
        unregister = registerShortcut(toggle, keyCombination, { target });
    });

    onDestroy(() => unregister?.());
</script>

<span
    class="plain-text-badge"
    class:visible={show || !animated}
    class:highlighted={!off}
    on:click|stopPropagation={toggle}
>
    <Badge
        tooltip="{tr.editingToggleVisualEditor()} ({getPlatformString(keyCombination)})"
        iconSize={80}
    >
        {@html richTextIcon}
    </Badge>
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
