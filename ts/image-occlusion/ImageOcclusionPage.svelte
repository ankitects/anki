<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import Container from "../components/Container.svelte";
    import { addOrUpdateNote } from "./generate";
    import type { IOMode } from "./lib";
    import MasksEditor from "./MaskEditor.svelte";
    import Notes from "./Notes.svelte";
    import StickyFooter from "./StickyFooter.svelte";

    export let mode: IOMode;

    async function hideAllGuessOne(): Promise<void> {
        addOrUpdateNote(mode, false);
    }

    async function hideOneGuessOne(): Promise<void> {
        addOrUpdateNote(mode, true);
    }

    const items = [
        { label: tr.notetypesOcclusionMask(), value: 1 },
        { label: tr.notetypesOcclusionNote(), value: 2 },
    ];

    let activeTabValue = 1;
    const tabChange = (tabValue) => () => (activeTabValue = tabValue);
</script>

<Container class="image-occlusion">
    <ul>
        {#each items as item}
            <li class={activeTabValue === item.value ? "active" : ""}>
                <span on:click={tabChange(item.value)}>{item.label}</span>
            </li>
        {/each}
    </ul>

    <div hidden={activeTabValue != 1}>
        <MasksEditor {mode} />
    </div>

    <div hidden={activeTabValue != 2}>
        <div class="notes-page"><Notes /></div>
        <StickyFooter
            {hideAllGuessOne}
            {hideOneGuessOne}
            editing={mode.kind == "edit"}
        />
    </div>
</Container>

<style lang="scss">
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
    ul {
        display: flex;
        flex-wrap: wrap;
        padding-left: 0;
        list-style: none;
        border-bottom: 1px solid var(--border);
        margin-top: 2px;
    }
    li {
        margin-bottom: -1px;
    }

    span {
        border: 1px solid transparent;
        border-top-left-radius: 0.25rem;
        border-top-right-radius: 0.25rem;
        display: block;
        padding: 0.5rem 1rem;
        cursor: pointer;
        color: var(--fg-subtle);
    }

    span:hover {
        border-color: var(--border) var(--border) var(--canvas);
    }

    li.active > span {
        color: var(--fg);
        background-color: var(--canvas);
        border-color: var(--border) var(--border) var(--canvas);
    }

    :global(.notes-page) {
        @media only screen and (min-width: 1024px) {
            width: min(100vw, 70em);
            margin: 6px auto;
            padding-bottom: 5em;
            padding-right: 12px;
        }
    }
</style>
