<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString, registerShortcut } from "@tslib/shortcuts";
    import { onEnterOrSpace } from "@tslib/keys";
    import { onMount } from "svelte";

    import Badge from "$lib/components/Badge.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import { stickyIconHollow } from "$lib/components/icons";
    import { stickyIconSolid } from "$lib/components/icons";

    import { context as editorFieldContext } from "./EditorField.svelte";
    import type { Note } from "@generated/anki/notes_pb";
    import { getNotetype, updateEditorNotetype } from "@generated/backend";
    import { bridgeCommand } from "@tslib/bridgecommand";

    const animated = !document.body.classList.contains("reduce-motion");

    export let active: boolean;
    export let show: boolean;
    export let isLegacy: boolean;

    const editorField = editorFieldContext.get();
    const keyCombination = "F9";

    export let index: number;
    export let note: Note;

    async function toggle() {
        if (isLegacy) {
            bridgeCommand(`toggleSticky:${index}`, (value: boolean) => {
                active = value;
            });
        } else {
            active = !active;
            const notetype = await getNotetype({ ntid: note.notetypeId });
            notetype.fields[index].config!.sticky = active;
            await updateEditorNotetype(notetype);
        }
    }

    function shortcut(target: HTMLElement): () => void {
        return registerShortcut(toggle, keyCombination, { target });
    }

    onMount(() => {
        editorField.element.then(shortcut);
    });
</script>

<span
    class:highlighted={active}
    class:visible={show || !animated}
    on:click|stopPropagation={toggle}
    on:keydown={onEnterOrSpace(() => toggle())}
    tabindex="-1"
    role="button"
>
    <Badge
        tooltip="{tr.editingToggleSticky()} ({getPlatformString(keyCombination)})"
        widthMultiplier={0.7}
    >
        {#if active}
            <Icon icon={stickyIconSolid} />
        {:else}
            <Icon icon={stickyIconHollow} />
        {/if}
    </Badge>
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
