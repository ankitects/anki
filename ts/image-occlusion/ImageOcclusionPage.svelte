<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import Container from "../components/Container.svelte";
    import { addOrUpdateNote } from "./add-or-update-note";
    import type { IOMode } from "./lib";
    import MasksEditor from "./MaskEditor.svelte";
    import Notes from "./Notes.svelte";
    import StickyFooter from "./StickyFooter.svelte";
    import { hideAllGuessOne, textEditingState } from "./store";

    export let mode: IOMode;

    async function addNote(): Promise<void> {
        addOrUpdateNote(mode, $hideAllGuessOne);
    }

    const items = [
        { label: tr.notetypesOcclusionMask(), value: 1 },
        { label: tr.notetypesOcclusionNote(), value: 2 },
    ];

    let activeTabValue = 1;
    const tabChange = (tabValue) => {
        textEditingState.set(tabValue === 2);
        activeTabValue = tabValue;
    };
</script>

<Container class="image-occlusion">
    <div class="tab-buttons">
        {#each items as item}
            <button
                class="tab-item {activeTabValue === item.value ? 'active' : ''} 
                    {item.value === 1 ? 'left-border-radius' : 'right-border-radius'}"
                on:click={() => tabChange(item.value)}
            >
                {item.label}
            </button>
        {/each}
    </div>

    <div hidden={activeTabValue != 1}>
        <MasksEditor {mode} on:change on:image-loaded />
    </div>

    <div hidden={activeTabValue != 2}>
        <Notes />
    </div>

    <StickyFooter {mode} {addNote} />
</Container>

<style lang="scss">
    .tab-buttons {
        display: flex;
        position: absolute;
        top: 2px;
        left: 2px;
    }
    .tab-buttons .active {
        background: var(--button-primary-bg);
        color: white;
    }

    .tab-item {
        justify-content: center;
        align-items: center;
        display: flex;
        padding: 0px 6px 0px 6px;
        height: 38px;
        max-width: 60px;
        font-size: small;
        white-space: normal;
        word-break: break-all;
        hyphens: auto;
    }

    :global(.image-occlusion) {
        --gutter-inline: 0.5rem;

        :global(.row) {
            // rows have negative margins by default
            --bs-gutter-x: 0;
            // ensure equal spacing between tall rows like
            // dropdowns, and short rows like checkboxes
            min-height: 2.5em;
            align-items: center;
        }
    }
</style>
