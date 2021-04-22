<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "anki/i18n";
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
        // show pop-up after dropdown has gone away
        setTimeout(() => {
            if (state.defaultConfigSelected()) {
                alert(tr.schedulingTheDefaultConfigurationCantBeRemoved());
                return;
            }
            // fixme: move tr.qt_misc schema mod msg into core
            // fixme: include name of deck in msg
            const msg = state.removalWilLForceFullSync()
                ? "This will require a one-way sync. Are you sure?"
                : "Are you sure?";
            if (confirm(msg)) {
                try {
                    state.removeCurrentConfig();
                } catch (err) {
                    alert(err);
                }
            }
        }, 100);
    }

    function save(applyToChildDecks: boolean): void {
        state.save(applyToChildDecks);
    }
</script>

<style>
    :global(svg) {
        vertical-align: text-bottom;
    }
</style>

<div class="btn-group" dir="ltr">
    <button
        type="button"
        class="btn btn-primary"
        on:click={() => save(false)}>Save</button>
    <button
        type="button"
        class="btn btn-secondary dropdown-toggle dropdown-toggle-split"
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
        <li>
            <a class="dropdown-item" href={'#'} on:click={() => save(true)}>Save to All
                Children</a>
        </li>
    </ul>
</div>
