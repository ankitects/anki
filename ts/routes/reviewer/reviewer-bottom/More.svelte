<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import * as tr from "@generated/ftl";
    import MoreSubmenu from "./MoreSubmenu.svelte";
    import MoreItem from "./MoreItem.svelte";

    let showFloating = false;
    let showFlags = false;

    let flags = [
        { colour: tr.actionsFlagRed(), shortcut: "Ctrl+1" },
        { colour: tr.actionsFlagOrange(), shortcut: "Ctrl+2" },
        { colour: tr.actionsFlagGreen(), shortcut: "Ctrl+3" },
        { colour: tr.actionsFlagBlue(), shortcut: "Ctrl+4" },
        { colour: tr.actionsFlagPink(), shortcut: "Ctrl+5" },
        { colour: tr.actionsFlagTurquoise(), shortcut: "Ctrl+6" },
        { colour: tr.actionsFlagPurple(), shortcut: "Ctrl+7" },
    ];
</script>

<MoreSubmenu bind:showFloating>
    <button
        slot="button"
        on:click={() => {
            showFloating = !showFloating;
        }}
        title={tr.actionsShortcutKey({ val: "M" })}
    >
        {tr.studyingMore()}&#8615
    </button>

    <div slot="items">
        <MoreSubmenu bind:showFloating={showFlags}>
            <DropdownItem
                slot="button"
                on:click={() => {
                    showFlags = !showFlags;
                }}
            >
                {tr.studyingFlagCard()}
            </DropdownItem>
            <div slot="items">
                {#each flags as flag}
                    <MoreItem shortcut={flag.shortcut}>{flag.colour}</MoreItem>
                {/each}
            </div>
        </MoreSubmenu>
    </div>
</MoreSubmenu>

<style>
    div :global(button) {
        width: fit-content;
    }
</style>
