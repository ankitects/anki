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
    import type { MoreMenuItemInfo } from "./types";
    import Shortcut from "$lib/components/Shortcut.svelte";

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
        alert("Not yet implemented in new reviewer.");
    }

    const shortcuts: MoreMenuItemInfo[] = [
        {
            name: tr.studyingBuryCard(),
            shortcut: "-",
            onClick: state.buryOrSuspendCurrentCard.bind(state, false),
        },
        {
            name: tr.actionsForgetCard(),
            shortcut: "Ctrl+Alt+N",
            onClick: state.displayForgetMenu.bind(state),
        },
        {
            name: tr.actionsSetDueDate(),
            shortcut: "Ctrl+Shift+D",
            onClick: state.displaySetDueDateMenu.bind(state),
        },
        {
            name: tr.actionsSuspendCard(),
            shortcut: "@",
            onClick: state.buryOrSuspendCurrentCard.bind(state, true),
        },
        {
            name: tr.actionsOptions(),
            shortcut: "O",
            onClick: state.displayOptionsMenu.bind(state),
        },
        {
            name: tr.actionsCardInfo(),
            shortcut: "I",
            onClick: state.displayCardInfoMenu.bind(state),
        },
        {
            name: tr.actionsPreviousCardInfo(),
            shortcut: "Ctrl+Alt+I",
            onClick: state.displayPreviousCardInfoMenu.bind(state),
        },

        "hr",
        // Notes
        {
            name: tr.studyingMarkNote(),
            shortcut: "*",
            onClick: state.toggleMarked.bind(state),
        },
        {
            name: tr.studyingBuryNote(),
            shortcut: "=",
            onClick: state.buryOrSuspendCurrentNote.bind(state, false),
        },
        {
            name: tr.studyingSuspendNote(),
            shortcut: "!",
            onClick: state.buryOrSuspendCurrentNote.bind(state, true),
        },
        {
            name: tr.actionsCreateCopy(),
            shortcut: "Ctrl+Alt+E",
            onClick: state.displayCreateCopyMenu.bind(state),
        },
        {
            name: tr.studyingDeleteNote(),
            shortcut: /* isMac ? "Ctrl+Backspace"  :*/ "Ctrl+Delete",
            onClick: state.deleteCurrentNote.bind(state),
        },

        "hr",
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

    const cardData = state.cardData;
    $: card = $cardData?.queue?.cards[0].card;

    function changeFlag(index: number) {
        if (card?.flags === index) {
            index = 0;
        }
        setFlag({ cardIds: [card!.id], flag: index });
        $cardData!.queue!.cards[0].card!.flags = index;
    }

    function prepKeycodeForShortcut(keycode: string) {
        return keycode.replace("Ctrl", "Control");
    }
</script>

{#each shortcuts as shortcut}
    {#if shortcut !== "hr"}
        <Shortcut
            keyCombination={prepKeycodeForShortcut(shortcut.shortcut)}
            event="keydown"
            on:action={shortcut.onClick}
        />
    {/if}
{/each}

{#each flags as flag, i}
    <Shortcut
        keyCombination={prepKeycodeForShortcut(flag.shortcut)}
        event="keydown"
        on:action={() => changeFlag(i + 1)}
    />
{/each}

<MoreSubmenu bind:showFloating bind:lockOpen={showFlags}>
    <button
        slot="button"
        on:click={() => {
            showFloating = !showFloating;
        }}
        title={tr.actionsShortcutKey({ val: "M" })}
    >
        {tr.studyingMore()}&nbsp;{"▾"}&nbsp;
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
                        {@const flag_id = i + 1}
                        <div
                            style:background-color={card?.flags == flag_id
                                ? `color-mix(in srgb, var(--flag-${flag_id}) 50%, transparent)`
                                : ""}
                        >
                            <MoreItem
                                shortcut={flag.shortcut}
                                on:click={() => changeFlag(flag_id)}
                            >
                                {flag.colour}
                            </MoreItem>
                        </div>
                    {/each}
                </div>
            </MoreSubmenu>
        </div>
        {#each shortcuts as shortcut}
            {#if shortcut == "hr"}
                <hr />
            {:else}
                <div
                    style:background-color={shortcut.onClick == todo
                        ? "RGBA(255,0,0,0.25)"
                        : ""}
                >
                    <MoreItem
                        shortcut={shortcut.shortcut}
                        on:click={shortcut.onClick}
                        on:keydown={shortcut.onClick}
                        on:action={console.log}
                    >
                        {shortcut.name}
                    </MoreItem>
                </div>
            {/if}
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

    hr {
        margin: 0;
    }

    button {
        line-height: 18px;
    }
</style>
