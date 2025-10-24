<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import {
        Mirror,
        type Options as OptionsProto,
        type ChooseVersionResponse,
        type GetMirrorsResponse_Pair,
    } from "@generated/anki/launcher_pb";
    import {
        chooseVersion,
        getAvailableVersions,
        getExistingVersions,
    } from "@generated/backend-launcher";
    import Row from "$lib/components/Row.svelte";
    import { tr } from "./stores";
    import Warning from "./Warning.svelte";
    import Action from "./Action.svelte";
    import Spinner from "./Spinner.svelte";
    import Options from "./Options.svelte";
    import Term from "./Term.svelte";
    import AnkiWillStart from "./AnkiWillStart.svelte";
    import type { Terminal } from "@xterm/xterm";
    import { tick } from "svelte";

    let {
        options,
        mirrors,
        uninstall,
        footer = $bindable(),
    }: {
        options: OptionsProto;
        mirrors: GetMirrorsResponse_Pair[];
        uninstall: (() => void) | null;
        footer: any; // https://github.com/sveltejs/svelte/issues/15182
    } = $props();

    footer = _footer;

    let releasesPromise = $state(getAvailableVersions({}, { alertOnError: false }));
    let existingPromise = $state(getExistingVersions({}, { alertOnError: false }));
    let loadPromise = $derived(Promise.all([releasesPromise, existingPromise]));

    let allowBetas = $state(options.allowBetas);
    let downloadCaching = $state(options.downloadCaching);
    let selectedMirror = $state(Mirror.DISABLED);

    let choosePromise: Promise<ChooseVersionResponse | null> = $state(
        Promise.resolve(null),
    );

    let error: Error | null = $state(null);
    const setError = (e: Error) => {
        tick().then(() => (error = e));
    };

    let term: Terminal | undefined = $state(undefined);
    let termOpen = $state(false);
    let chosen = $state(false);

    const choose = (version: string, keepExisting: boolean, current?: string) => {
        chosen = true;
        term?.reset();
        choosePromise = chooseVersion({
            version,
            keepExisting,
            options: { allowBetas, downloadCaching, mirror: selectedMirror },
            ...(current ? { current } : {}),
        });
    };
</script>

{#await choosePromise}
    <Row class="centre m-3">
        <Spinner label={$tr.launcherSyncing()} />
    </Row>
{:then res}
    {#if res === null}
        {#await loadPromise}
            <Row class="centre m-3">
                <Spinner label={$tr.launcherLoadingVersions()} />
            </Row>
        {:then [releases, existing]}
            <Action {releases} {existing} {allowBetas} {choose} {uninstall} />
        {:catch e}
            {setError(e)}
            <Warning
                warning={$tr.lauuncherFailedToLoadVersions()}
                className="alert-danger"
            />
        {/await}
    {:else}
        <Row class="centre m-3">
            <AnkiWillStart {res} />
        </Row>
    {/if}
{:catch e}
    {setError(e)}
    <Warning warning={$tr.launcherFailedToSync()} className="alert-danger" />
{/await}
{#if error != null}
    <Row>
        <pre>{error.message}</pre>
    </Row>
{/if}

{#snippet _footer()}
    <Term bind:term bind:open={termOpen} />
    {#if !chosen}
        <Row class="row-columns">
            <Options
                {mirrors}
                bind:allowBetas
                bind:downloadCaching
                bind:selectedMirror
            />
        </Row>
    {/if}
{/snippet}

<style lang="scss">
    :root {
        font-size: 16px;
        font-weight: 400;

        text-rendering: optimizeLegibility;
        font-synthesis: none;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
        -webkit-text-size-adjust: 100%;
    }

    .logo {
        max-width: 50px;
        margin-inline-end: 1em;

        -webkit-user-drag: none;
        user-select: none;
        -moz-user-select: none;
        -webkit-user-select: none;
        -ms-user-select: none;
    }

    .title {
        align-items: center;
    }

    :global(.centre) {
        justify-content: center;
    }

    pre {
        white-space: pre-wrap; /* Since CSS 2.1 */
        white-space: -moz-pre-wrap; /* Mozilla, since 1999 */
        white-space: -pre-wrap; /* Opera 4-6 */
        white-space: -o-pre-wrap; /* Opera 7 */
        word-wrap: break-word; /* Internet Explorer 5.5+ */
    }
</style>
