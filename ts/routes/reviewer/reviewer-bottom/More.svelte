<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
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

    function todo() {
        alert("Not Yet Implemented");
    }

    const shortcuts = [
        {
            name: tr.studyingBuryCard(),
            shortcut: "-",
            onClick: state.buryCurrentCard.bind(state),
        },
        { name: tr.actionsForgetCard(), shortcut: "Ctrl+Alt+N", onClick: todo },
        { name: tr.actionsSetDueDate(), shortcut: "Ctrl+Shift+D", onClick: todo },
        { name: tr.actionsSuspendCard(), shortcut: "@", onClick: todo },
        { name: tr.actionsOptions(), shortcut: "O", onClick: todo },
        { name: tr.actionsCardInfo(), shortcut: "I", onClick: todo },
        { name: tr.actionsPreviousCardInfo(), shortcut: "Ctrl+Alt+I", onClick: todo },

        // Notes
        { name: tr.studyingMarkNote(), shortcut: "*", onClick: todo },
        { name: tr.studyingBuryNote(), shortcut: "=", onClick: todo },
        { name: tr.studyingSuspendNote(), shortcut: "!", onClick: todo },
        { name: tr.actionsCreateCopy(), shortcut: "Ctrl+Alt+E", onClick: todo },
        {
            name: tr.studyingDeleteNote(),
            shortcut: /* isMac ? "Ctrl+Backspace"  :*/ "Ctrl+Delete",
            onClick: todo,
        },

        // Audio
        { name: tr.actionsReplayAudio(), shortcut: "R", onClick: todo },
        { name: tr.studyingPauseAudio(), shortcut: "5", onClick: todo },
        { name: tr.studyingAudio5s(), shortcut: "6", onClick: todo },
        { name: tr.studyingAudioAnd5s(), shortcut: "7", onClick: todo },
        { name: tr.studyingRecordOwnVoice(), shortcut: "Shift+V", onClick: todo },
        { name: tr.studyingReplayOwnVoice(), shortcut: "V", onClick: todo },
        {
            name: tr.actionsAutoAdvance(),
            shortcut: "Shift+A",
            onClick: todo /* checked: autoAdvanceEnabled */,
        },
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
                <MoreItem
                    slot="button"
                    on:click={() => {
                        showFlags = !showFlags;
                    }}
                >
                    {tr.studyingFlagCard()}
                </MoreItem>
                <div slot="items" class="dropdown">
                    {#each flags as flag, i}
                        <MoreItem
                            shortcut={flag.shortcut}
                            on:click={() => changeFlag(i + 1)}
                        >
                            {flag.colour}
                        </MoreItem>
                    {/each}
                </div>
            </MoreSubmenu>
        </div>
        {#each shortcuts as shortcut}
            <MoreItem shortcut={shortcut.shortcut} on:click={shortcut.onClick}>
                {shortcut.name}
            </MoreItem>
        {/each}
    </div>
</MoreSubmenu>

<style lang="scss">
    div.dropdown {
        :global(button) {
            border-radius: 0;
            padding: 0.5em;
            margin: 0;

            &:hover {
                background: inherit;
                color: inherit;
            }
        }
    }

    button {
        line-height: 18px;
    }
</style>
