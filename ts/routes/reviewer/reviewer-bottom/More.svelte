<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import * as tr from "@generated/ftl";
    import MoreSubmenu from "./MoreSubmenu.svelte";
    import MoreItem from "./MoreItem.svelte";
    import { setFlag } from "@generated/backend";
    import type { ReviewerState } from "../reviewer";

    let showFloating = false;
    let showFlags = false;
    export let state: ReviewerState;

    const flags = [
        { colour: tr.actionsFlagRed(), shortcut: "Ctrl+1" },
        { colour: tr.actionsFlagOrange(), shortcut: "Ctrl+2" },
        { colour: tr.actionsFlagGreen(), shortcut: "Ctrl+3" },
        { colour: tr.actionsFlagBlue(), shortcut: "Ctrl+4" },
        { colour: tr.actionsFlagPink(), shortcut: "Ctrl+5" },
        { colour: tr.actionsFlagTurquoise(), shortcut: "Ctrl+6" },
        { colour: tr.actionsFlagPurple(), shortcut: "Ctrl+7" },
    ];

    const shortcuts = [
        { onClick: state.buryCurrentCard.bind(state), name: "bury", shortcut: "~" },
    ];

    function changeFlag(index: number) {
        setFlag({ cardIds: [state.currentCard!.card!.id], flag: index });
        state.cardData.update(($cardData) => {
            $cardData!.queue!.cards[0].card!.flags = index;
            return $cardData;
        });
    }
</script>

<MoreSubmenu bind:showFloating>
    <button
        slot="button"
        on:click={() => {
            showFloating = !showFloating;
        }}
        title={tr.actionsShortcutKey({ val: "M" })}
    >
        {tr.studyingMore()}{"▾"}
    </button>

    <div slot="items" class="dropdown">
        <div class="row">
            <MoreSubmenu bind:showFloating={showFlags}>
                <DropdownItem
                    slot="button"
                    on:click={() => {
                        showFlags = !showFlags;
                    }}
                >
                    {tr.studyingFlagCard()}
                </DropdownItem>
                <div slot="items" class="dropdown">
                    {#each flags as flag, i}
                        <div class="row">
                            <MoreItem
                                shortcut={flag.shortcut}
                                onClick={() => changeFlag(i + 1)}
                            >
                                {flag.colour}
                            </MoreItem>
                            <span>{flag.shortcut}</span>
                        </div>
                    {/each}
                </div>
            </MoreSubmenu>
        </div>
        <span></span>
        {#each shortcuts as shortcut}
            <div class="row">
                <div>
                    <MoreItem shortcut={shortcut.shortcut} onClick={shortcut.onClick}>
                        {shortcut.name}
                    </MoreItem>
                </div>
                <span>
                    {shortcut.shortcut}
                </span>
            </div>
        {/each}
    </div>
</MoreSubmenu>

<style lang="scss">
    div.dropdown {
        display: grid;
        grid-template-columns: 1fr auto;
        align-items: baseline;

        :global(button) {
            border-radius: 0;
            padding: 0.5em;
            margin: 0;

            // TODO: Hover effects
            &:hover {
                background: inherit;
                color: inherit;
            }
        }
    }

    div.row {
        display: contents;
    }

    button {
        line-height: 18px;
    }
</style>
