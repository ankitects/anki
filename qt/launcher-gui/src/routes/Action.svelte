<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tr } from "./stores";
    import type { ExistingVersions, Versions } from "@generated/anki/launcher_pb";
    import Row from "$lib/components/Row.svelte";
    import EnumSelector from "$lib/components/EnumSelector.svelte";
    import Warning from "./Warning.svelte";
    import Spinner from "./Spinner.svelte";

    let {
        releasesPromise,
        existingPromise,
        allowBetas,
        choose,
        uninstall,
    }: {
        releasesPromise: Promise<Versions>;
        existingPromise: Promise<ExistingVersions>;
        allowBetas: boolean;
        choose: (version: string, existing: boolean, current?: string) => void;
        uninstall: (() => void) | null;
    } = $props();

    // TODO: replace once svelte's experimental async mode is on
    let releases = $state(undefined as Versions | undefined);
    let existing = $state(undefined as ExistingVersions | undefined);
    releasesPromise.then((r) => (releases = r));
    existingPromise.then((r) => (existing = r));

    let availableVersions = $derived(
        releases?.all
            .filter((v) => allowBetas || !v.isPrerelease)
            .map((v) => ({ label: v.version, value: v.version })),
    );

    let latest = $derived(availableVersions?.[0]?.value);
    let selected = $derived(availableVersions?.[0]?.value);
    let current = $derived(existing!?.current?.version);
    let pyprojectModified = $derived(existing?.pyprojectModifiedByUser);

    function _choose(version: string, keepExisting: boolean = false) {
        choose(version, keepExisting, current);
    }
</script>

<div class="group">
    {#await releasesPromise}
        <Spinner label={$tr.launcherLoadingVersions()} />
    {:then}
        {#if latest != null && latest != current}
            <Row class="centre m-3">
                <button class="btn btn-primary" onclick={() => _choose(latest)}>
                    {#if latest == null}
                        {$tr.launcherLatestAnki()}
                    {:else}
                        {$tr.launcherLatestAnkiVersion({
                            version: latest!,
                        })}
                    {/if}
                </button>
            </Row>
        {/if}
    {:catch}
        <Warning warning={$tr.launcherFailedToGetReleases()} className="alert-danger" />
    {/await}
    {#await existingPromise}
        <Spinner label={$tr.launcherCheckingExisting()} />
    {:then}
        {#if current != null}
            <Row class="centre m-3">
                <button class="btn btn-primary" onclick={() => _choose(current, true)}>
                    {#if pyprojectModified}
                        {$tr.launcherSyncProjectChanges()}
                    {:else}
                        {$tr.launcherKeepExistingVersion({ current })}
                    {/if}
                </button>
            </Row>
        {/if}
    {/await}
    {#if availableVersions}
        <Row class="centre m-3">
            <button
                class="btn btn-primary"
                onclick={() => _choose(selected!)}
                disabled={selected == null}
            >
                {$tr.launcherChooseAVersion()}
            </button>
            <div class="m-2">
                {"->"}
            </div>
            <div style="width: 100px">
                <EnumSelector bind:value={selected} choices={availableVersions} />
            </div>
        </Row>
    {/if}
    {#if uninstall != null}
        <Row class="centre m-3">
            <button class="btn btn-primary" onclick={uninstall}>
                {$tr.launcherUninstall()}
            </button>
        </Row>
    {/if}
</div>

<style lang="scss">
    :global(.centre) {
        justify-content: center;
    }

    .group {
        margin-top: 1em;
    }
</style>
