<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import MoreSubmenu from "./MoreSubmenu.svelte";
    import MoreItem from "./MoreItem.svelte";
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
        {
            name: tr.actionsReplayAudio(),
            shortcut: "R",
            onClick: state.replayAudio.bind(state),
        },
        {
            name: tr.studyingPauseAudio(),
            shortcut: "5",
            onClick: state.pauseAudio.bind(state),
        },
        {
            name: tr.studyingAudio5s(),
            shortcut: "6",
            onClick: state.AudioSeekBackward.bind(state),
        },
        {
            name: tr.studyingAudioAnd5s(),
            shortcut: "7",
            onClick: state.AudioSeekForward.bind(state),
        },
        {
            name: tr.studyingRecordOwnVoice(),
            shortcut: "Shift+V",
            onClick: state.RecordVoice.bind(state),
        },
        {
            name: tr.studyingReplayOwnVoice(),
            shortcut: "V",
            onClick: state.ReplayRecorded.bind(state),
        },
        {
            name: tr.actionsAutoAdvance(),
            shortcut: "Shift+A",
            onClick: () => state.toggleAutoAdvance(),
        },
    ];

    $: currentFlag = state.flag;
    $: autoAdvance = state.autoAdvance;

    function prepKeycodeForShortcut(keycode: string) {
        return keycode.replace("Ctrl", "Control");
    }
</script>

<Shortcut
    keyCombination="m"
    event="keyup"
    on:action={() => (showFloating = !showFloating)}
/>

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
        on:action={() => {
            state.changeFlag(i + 1);
        }}
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
                            style:background-color={$currentFlag == flag_id
                                ? `color-mix(in srgb, var(--flag-${flag_id}) 50%, transparent)`
                                : ""}
                        >
                            <MoreItem
                                shortcut={flag.shortcut}
                                on:click={() => {
                                    state.changeFlag(flag_id);
                                }}
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
                {@const highlighted = shortcut.shortcut == "Shift+A" && $autoAdvance}
                <div style:background-color={highlighted ? "RGBA(0,255,0,0.25)" : ""}>
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

        display: flex;
        flex-direction: column;
        flex-wrap: wrap;
        max-height: 90vh;
    }

    hr {
        margin: 0;
    }

    button {
        line-height: 18px;
    }
</style>
