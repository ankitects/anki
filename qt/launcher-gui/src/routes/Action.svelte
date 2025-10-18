<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tr } from "./stores";
    import { ExistingVersions, Versions } from "@generated/anki/launcher_pb";
    import Row from "$lib/components/Row.svelte";
    import EnumSelector from "$lib/components/EnumSelector.svelte";

    let {
        releases,
        existing,
        allowBetas,
        choose = (_, __) => {},
    }: {
        releases: Versions;
        existing: ExistingVersions;
        allowBetas: boolean;
        choose: (version: string, existing: boolean, current?: string) => void;
    } = $props();

    const availableVersions = $derived(
        releases.all
            .filter((v) => allowBetas || !v.isPrerelease)
            .map((v) => ({ label: v.version, value: v.version })),
    );

    let latest = $derived(availableVersions[0]?.value ?? null);
    let selected = $derived(availableVersions[0]?.value ?? null);
    let current = $derived(existing.current?.version);

    let pyprojectModified = existing.pyprojectModifiedByUser;

    function _choose(version: string, keepExisting: boolean = false) {
        choose(version, keepExisting, current);
    }
</script>

<div class="group">
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
</div>

<style lang="scss">
    :global(.centre) {
        justify-content: center;
    }

    .group {
        margin-top: 1em;
    }
</style>
