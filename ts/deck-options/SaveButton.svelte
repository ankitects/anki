<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { UpdateDeckConfigsMode } from "@tslib/anki/deck_config_pb";
    import * as tr from "@tslib/ftl";
    import { withCollapsedWhitespace } from "@tslib/i18n";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher, tick } from "svelte";
    import { get } from "svelte/store";

    import DropdownDivider from "../components/DropdownDivider.svelte";
    import DropdownItem from "../components/DropdownItem.svelte";
    import IconButton from "../components/IconButton.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Popover from "../components/Popover.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import WithFloating from "../components/WithFloating.svelte";
    import { chevronDown } from "./icons";
    import type { DeckOptionsState } from "./lib";

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    const dispatch = createEventDispatcher();

    export let state: DeckOptionsState;

    /** Ensure blur handler has fired so changes get committed. */
    async function commitEditing(): Promise<void> {
        if (document.activeElement instanceof HTMLElement) {
            document.activeElement.blur();
        }
        await tick();
    }

    async function removeConfig(): Promise<void> {
        // show pop-up after dropdown has gone away
        await tick();

        if (state.defaultConfigSelected()) {
            alert(tr.schedulingTheDefaultConfigurationCantBeRemoved());
            return;
        }

        const msg =
            (state.removalWilLForceFullSync()
                ? tr.deckConfigWillRequireFullSync() + " "
                : "") +
            tr.deckConfigConfirmRemoveName({ name: state.getCurrentName() });

        if (confirm(withCollapsedWhitespace(msg))) {
            try {
                state.removeCurrentConfig();
                dispatch("remove");
            } catch (err) {
                alert(err);
            }
        }
    }

    async function save(mode: UpdateDeckConfigsMode): Promise<void> {
        await commitEditing();
        if (mode === UpdateDeckConfigsMode.COMPUTE_ALL_WEIGHTS && !get(state.fsrs)) {
            alert(tr.deckConfigFsrsMustBeEnabled());
            return;
        }
        state.save(mode);
    }

    const saveKeyCombination = "Control+Enter";

    let showFloating = false;
</script>

<LabelButton
    primary
    on:click={() => save(UpdateDeckConfigsMode.NORMAL)}
    tooltip={getPlatformString(saveKeyCombination)}
    --border-left-radius={!rtl ? "var(--border-radius)" : "0"}
    --border-right-radius={rtl ? "var(--border-radius)" : "0"}
>
    <div class="save">{tr.deckConfigSaveButton()}</div>
</LabelButton>
<Shortcut
    keyCombination={saveKeyCombination}
    on:action={() => save(UpdateDeckConfigsMode.NORMAL)}
/>

<WithFloating
    show={showFloating}
    closeOnInsideClick
    inline
    on:close={() => (showFloating = false)}
>
    <IconButton
        class="chevron"
        slot="reference"
        on:click={() => (showFloating = !showFloating)}
        --border-right-radius={!rtl ? "var(--border-radius)" : "0"}
        --border-left-radius={rtl ? "var(--border-radius)" : "0"}
        iconSize={80}
    >
        {@html chevronDown}
    </IconButton>
    <Popover slot="floating">
        <DropdownItem on:click={() => dispatch("add")}>
            {tr.deckConfigAddGroup()}
        </DropdownItem>
        <DropdownItem on:click={() => dispatch("clone")}>
            {tr.deckConfigCloneGroup()}
        </DropdownItem>
        <DropdownItem on:click={() => dispatch("rename")}>
            {tr.deckConfigRenameGroup()}
        </DropdownItem>
        <DropdownItem on:click={removeConfig}>
            {tr.deckConfigRemoveGroup()}
        </DropdownItem>
        <DropdownDivider />
        <DropdownItem on:click={() => save(UpdateDeckConfigsMode.APPLY_TO_CHILDREN)}>
            {tr.deckConfigSaveToAllSubdecks()}
        </DropdownItem>
        <DropdownItem on:click={() => save(UpdateDeckConfigsMode.COMPUTE_ALL_WEIGHTS)}>
            {tr.deckConfigSaveAndOptimize()}
        </DropdownItem>
    </Popover>
</WithFloating>

<style lang="scss">
    .save {
        margin: 0 0.75rem;
    }

    /* Todo: find more elegant fix for misalignment */
    :global(.chevron) {
        height: 100% !important;
    }
</style>
