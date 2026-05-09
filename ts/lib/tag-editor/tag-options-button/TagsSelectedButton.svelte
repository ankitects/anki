<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { tagActionsShortcutsKey } from "@tslib/context-keys";
    import { onEnterOrSpace } from "@tslib/keys";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher, getContext } from "svelte";

    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconConstrain from "$lib/components/IconConstrain.svelte";
    import { dotsIcon } from "$lib/components/icons";
    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";

    const dispatch = createEventDispatcher();

    let show = false;

    const { selectAllShortcut, copyShortcut, removeShortcut } =
        getContext<Record<string, string>>(tagActionsShortcutsKey);
</script>

<WithFloating
    {show}
    preferredPlacement="top"
    portalTarget={document.body}
    shift={0}
    let:asReference
>
    <div
        class="tags-selected-button"
        use:asReference
        role="button"
        tabindex="0"
        on:click={() => (show = !show)}
        on:keydown={onEnterOrSpace(() => (show = !show))}
    >
        <IconConstrain><Icon icon={dotsIcon} /></IconConstrain>
    </div>

    <Popover slot="floating">
        <DropdownItem on:click={() => dispatch("tagselectall")}>
            {tr.editingTagsSelectAll()} ({getPlatformString(selectAllShortcut)})
        </DropdownItem>

        <DropdownItem on:click={() => dispatch("tagcopy")}>
            {tr.editingTagsCopy()} ({getPlatformString(copyShortcut)})
        </DropdownItem>

        <DropdownItem on:click={() => dispatch("tagdelete")}>
            {tr.editingTagsRemove()} ({getPlatformString(removeShortcut)})
        </DropdownItem>
    </Popover>
</WithFloating>

<style lang="scss">
    .tags-selected-button {
        line-height: 1;
        :global(svg) {
            cursor: pointer;
            fill: currentColor;
            opacity: 0.6;
        }

        :global(svg:hover) {
            opacity: 1;
        }
    }
</style>
