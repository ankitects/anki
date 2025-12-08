<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tr } from "./stores";
    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import { Mirror } from "@generated/anki/launcher_pb";

    let {
        allowBetas = $bindable(),
        downloadCaching = $bindable(),
        mirrors,
        selectedMirror = $bindable(),
    } = $props();

    const availableMirrors = $derived(
        mirrors.map(({ mirror, name }) => ({
            label: name,
            value: mirror,
        })),
    );

    // only the labels are expected to change
    // svelte-ignore state_referenced_locally
    selectedMirror = availableMirrors[0].value ?? Mirror.DISABLED;
</script>

<TitledContainer title={$tr.launcherAdvanced()}>
    <div class="m-2">
        <SwitchRow bind:value={allowBetas} defaultValue={allowBetas} hideRevert>
            <SettingTitle>
                {$tr.launcherAllowBetasToggle()}
            </SettingTitle>
        </SwitchRow>
    </div>
    <div class="m-2">
        <SwitchRow
            bind:value={downloadCaching}
            defaultValue={downloadCaching}
            hideRevert
        >
            <SettingTitle>
                {$tr.launcherDownloadCaching()}
            </SettingTitle>
        </SwitchRow>
    </div>
    <div class="m-2">
        <EnumSelectorRow
            breakpoint="sm"
            bind:value={selectedMirror}
            choices={availableMirrors}
            defaultValue={selectedMirror}
            hideRevert
        >
            <SettingTitle>
                {$tr.launcherUseMirror()}
            </SettingTitle>
        </EnumSelectorRow>
    </div>
</TitledContainer>
