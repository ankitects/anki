<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    import { uninstallAnki } from "@generated/backend-launcher";
    import type { Uninstall, UninstallResponse } from "@generated/anki/launcher_pb";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import Row from "$lib/components/Row.svelte";
    import Warning from "./Warning.svelte";
    import Spinner from "./Spinner.svelte";
    import { tr } from "./stores";

    let {
        uninstallInfo,
        footer = $bindable(),
    }: { uninstallInfo: Uninstall; footer: any } = $props();

    footer = null;

    let uninstallPromise: Promise<UninstallResponse | void> = $state(Promise.resolve());

    let deleteBaseFolder = $state(false);

    function confirmUninstall() {
        uninstallPromise = uninstallAnki({ deleteBaseFolder });
    }
</script>

{#await uninstallPromise}
    <Row class="centre m-3">
        <Spinner label={$tr.launcherUninstalling()} />
    </Row>
{:then res}
    {#if !res}
        <div class="group">
            <SwitchRow
                bind:value={deleteBaseFolder}
                defaultValue={false}
                disabled={!uninstallInfo.ankiBaseFolderExists}
                hideRevert
            >
                <SettingTitle>
                    {$tr.launcherRemoveAllProfilesConfirm()}
                </SettingTitle>
            </SwitchRow>
            {#if deleteBaseFolder}
                <Warning
                    warning={$tr.launcherRemoveProfilesWarning()}
                    className="alert-danger"
                />
            {/if}
            <Row class="centre m-3">
                <button class="btn btn-primary" onclick={confirmUninstall}>
                    {deleteBaseFolder
                        ? $tr.launcherUninstallConfirmAndRemoveProfiles()
                        : $tr.launcherUninstallConfirm()}
                </button>
            </Row>
        </div>
    {:else}
        {@const kind = res.actionNeeded?.case}
        {#if !kind}
            <Row class="centre">
                <Warning
                    warning={$tr.launcherUninstallComplete()}
                    className="alert-success"
                />
            </Row>
        {:else}
            <Row class="centre">
                <Warning
                    warning={$tr.launcherUninstallActionNeeded()}
                    className="alert-warning"
                />
            </Row>
            {#if kind === "unixScript"}
                <Row class="centre mb-3">
                    {$tr.launcherUninstallUnix({ path: res.actionNeeded.value })}
                </Row>
            {:else if kind === "macManual"}
                <Row class="centre mb-3">
                    {$tr.launcherUninstallMac()}
                </Row>
            {:else if kind === "windowsInstallerNotFound"}
                <Row class="centre mb-3">
                    {$tr.launcherUninstallWinNotFound()}
                </Row>
                <Row class="centre mb-3">
                    {$tr.launcherUninstallWinNotFoundExtra()}
                </Row>
            {:else}
                {@const { error, path } = res.actionNeeded.value}
                <Row class="centre mb-3">
                    {$tr.launcherUninstallWinFailed()}
                </Row>
                <Row class="centre mb-3">
                    {$tr.launcherUninstallWinFailedExtra({ path })}
                </Row>
                <Row>
                    <pre>{error}</pre>
                </Row>
            {/if}
        {/if}
    {/if}
{:catch e}
    <Warning warning={$tr.launcherFailedToUninstall()} className="alert-danger" />
    <Row>
        <pre>{e.message}</pre>
    </Row>
{/await}

<style lang="scss">
    .group {
        margin-top: 1em;
    }

    :global(.centre) {
        justify-content: center;
        text-align: center;
    }
</style>
