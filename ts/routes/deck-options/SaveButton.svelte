<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { UpdateDeckConfigsMode } from "@generated/anki/deck_config_pb";
    import * as tr from "@generated/ftl";
    import { withCollapsedWhitespace } from "@tslib/i18n";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher, tick } from "svelte";

    import DropdownDivider from "$lib/components/DropdownDivider.svelte";
    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { chevronDown } from "$lib/components/icons";
    import LabelButton from "$lib/components/LabelButton.svelte";
    import Popover from "$lib/components/Popover.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";

    import type { DeckOptionsState } from "./lib";
    import { commitEditing } from "./lib";

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    const dispatch = createEventDispatcher();

    export let state: DeckOptionsState;

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
        state.save(mode);
    }

    const saveKeyCombination = "Control+Enter";

    let showFloating = false;
</script>

<div class="d-flex">
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
            <Icon icon={chevronDown} />
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
            <DropdownItem
                on:click={() => save(UpdateDeckConfigsMode.APPLY_TO_CHILDREN)}
            >
                {tr.deckConfigSaveToAllSubdecks()}
            </DropdownItem>
        </Popover>
    </WithFloating>
</div>

<style lang="scss">
    .save {
        margin: 0 0.75rem;
    }

    /* Todo: find more elegant fix for misalignment */
    :global(.chevron) {
        height: 100% !important;
    }
</style>
