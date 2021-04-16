<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    // import * as tr from "anki/i18n";
    import { textInputModal } from "./textInputModal";
    import type { DeckConfigState } from "./lib";

    export let state: DeckConfigState;

    function addConfig(): void {
        textInputModal({
            title: "Add Config",
            prompt: "Name:",
            onOk: (text: string) => {
                const trimmed = text.trim();
                if (trimmed.length) {
                    state.addConfig(trimmed);
                }
            },
        });
    }

    function renameConfig(): void {
        textInputModal({
            title: "Rename Config",
            prompt: "Name:",
            startingValue: state.getCurrentName(),
            onOk: (text: string) => {
                state.setCurrentName(text);
            },
        });
    }

    function removeConfig(): void {
        setTimeout(() => {
            if (confirm("Are you sure?")) {
                try {
                    state.removeCurrentConfig();
                } catch (err) {
                    alert(err);
                }
            }
        }, 100);
    }
</script>

<style>
    :global(svg) {
        vertical-align: text-bottom;
    }
</style>

<div class="btn-group">
    <button type="button" class="btn btn-primary">Save</button>
    <button
        type="button"
        class="btn btn-primary dropdown-toggle dropdown-toggle-split"
        data-bs-toggle="dropdown"
        aria-expanded="false">
        <span class="visually-hidden">Toggle Dropdown</span>
    </button>
    <ul class="dropdown-menu">
        <li><a class="dropdown-item" href={'#'} on:click={addConfig}>Add Config</a></li>
        <li>
            <a class="dropdown-item" href={'#'} on:click={renameConfig}>Rename Config</a>
        </li>
        <li>
            <a class="dropdown-item" href={'#'} on:click={removeConfig}>Remove Config</a>
        </li>
        <li>
            <hr class="dropdown-divider" />
        </li>
        <li><a class="dropdown-item" href={'#'}>Apply to Child Decks</a></li>
    </ul>
</div>
