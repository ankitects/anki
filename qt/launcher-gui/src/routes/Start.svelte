<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl-launcher";
    import {
        Mirror,
        type Options,
        type ChooseVersionResponse,
        type GetLangsResponse_Pair,
        type GetMirrorsResponse_Pair,
    } from "@generated/anki/launcher_pb";
    import { chooseVersion } from "@generated/backend-launcher";
    import Row from "$lib/components/Row.svelte";
    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import Container from "$lib/components/Container.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import EnumSelector from "$lib/components/EnumSelector.svelte";
    import Warning from "./Warning.svelte";
    import { listen } from "@tauri-apps/api/event";
    import { protoBase64 } from "@bufbuild/protobuf";
    import { currentLang, versionsStore } from "./stores";
    import { onMount } from "svelte";
    import { Terminal } from "@xterm/xterm";
    import "@xterm/xterm/css/xterm.css";

    // TODO: why
    /* eslint-disable prefer-const */
    let {
        langs,
        selectedLang = $bindable(),
        options,
        mirrors,
    }: {
        langs: GetLangsResponse_Pair[];
        selectedLang: string;
        options: Options;
        mirrors: GetMirrorsResponse_Pair[];
    } = $props();
    /* eslint-enable prefer-const */

    const availableLangs = $derived(
        langs.map((p) => ({ label: p.name, value: p.locale })),
    );

    const availableMirrors = $derived(
        mirrors.map(({ mirror, name }) => ({
            label: name,
            value: mirror,
        })),
    );
    // only the labels are expected to change
    // svelte-ignore state_referenced_locally
    let selectedMirror = $state(availableMirrors[0].value ?? Mirror.DISABLED);

    let allowBetas: boolean = $state(options.allowBetas);
    let downloadCaching: boolean = $state(options.downloadCaching);

    const availableVersions = $derived(
        $versionsStore?.all.map((v) => ({ label: v, value: v })) ?? [],
    );
    // const availableLatestVersions = $derived($versionsStore?.latest ?? []);
    let selectedVersion = $derived(availableVersions[0]?.value);
    /* eslint-disable prefer-const */
    let currentVersion = $derived($versionsStore?.current);
    let latestVersion = $derived($versionsStore?.latest[0]);
    /* eslint-enable prefer-const */

    let choosePromise: Promise<ChooseVersionResponse | null> = $state(
        Promise.resolve(null),
    );

    const choose = (version: string, keepExisting: boolean) => {
        choosePromise = chooseVersion({
            version,
            keepExisting,
            options: { allowBetas, downloadCaching, mirror: selectedMirror },
        });
    };

    let termRef: HTMLDivElement;
    let termTabRef: HTMLDetailsElement;

    onMount(() => {
        const term = new Terminal({
            disableStdin: true,
            rows: 12,
            cols: 60,
            cursorStyle: "underline",
            cursorInactiveStyle: "none",
            // TODO: saw this in the docs, but do we need it?
            windowsMode: navigator.platform.indexOf("Win") != -1,
        });

        term.open(termRef);

        termRef.oncontextmenu = (e) => {
            e.preventDefault();
            term.selectAll();
            const lines = term.getSelection().trim();
            term.clearSelection();
            navigator.clipboard.writeText(lines);
        };

        const unlisten = listen<string>("pty-data", (e) => {
            const data = protoBase64.dec(e.payload);
            if (!termTabRef.open) {
                termTabRef.open = true;
            }
            term.write(data);
        });

        return () => {
            term.dispose();
            unlisten.then((cb) => cb());
        };
    });

    // const zoomIn = () => ($zoomFactor = Math.min($zoomFactor + 0.1, 3));
    // const zoomOut = () => ($zoomFactor = Math.max($zoomFactor - 0.1, 0.5));
</script>

<!-- TODO: this breaks scrolling on wsl, fine on win -->
<!-- <svelte:window -->
<!--     onwheel={(e) => { -->
<!--         if (!e.ctrlKey) { -->
<!--             return true; -->
<!--         } -->
<!--         e.preventDefault(); -->
<!--         e.deltaY < 0 ? zoomIn() : zoomOut(); -->
<!--     }} -->
<!-- /> -->

<Container
    breakpoint="sm"
    --gutter-inline="0.25rem"
    --gutter-block="0.75rem"
    class="container-columns"
>
    {#key $currentLang}
        <Row class="row-columns">
            <TitledContainer title={""}>
                <Row --cols={2} slot="title" class="title">
                    <img src="/anki.png" alt="logo" class="logo" />
                    <h1 class="title">{tr.launcherTitle()}</h1>
                </Row>
                <EnumSelectorRow
                    breakpoint="sm"
                    bind:value={selectedLang}
                    choices={availableLangs}
                    defaultValue={selectedLang}
                    hideRevert
                >
                    <SettingTitle>
                        {tr.launcherLanguage()}
                    </SettingTitle>
                </EnumSelectorRow>
                <div class="group">
                    {#if latestVersion != null && latestVersion != currentVersion}
                        <Row class="centre m-3">
                            <button
                                class="btn btn-primary"
                                onclick={() => choose(latestVersion, false)}
                            >
                                {#if latestVersion == null}
                                    {tr.launcherLatestAnki()}
                                {:else}
                                    {tr.launcherLatestAnkiVersion({
                                        version: latestVersion!,
                                    })}
                                {/if}
                            </button>
                        </Row>
                    {/if}
                    {#if currentVersion != null}
                        <Row class="centre m-3">
                            <button
                                class="btn btn-primary"
                                onclick={() => choose(currentVersion, true)}
                            >
                                {tr.launcherKeepExistingVersion({
                                    current: currentVersion ?? "N/A",
                                })}
                            </button>
                        </Row>
                    {/if}
                    <Row class="centre m-3">
                        <button
                            class="btn btn-primary"
                            onclick={() => choose(selectedVersion!, false)}
                            disabled={selectedVersion == null}
                        >
                            {tr.launcherChooseAVersion()}
                        </button>
                        <div class="m-2">
                            {"->"}
                        </div>
                        <div style="width: 100px">
                            {#if availableVersions.length !== 0}
                                <EnumSelector
                                    bind:value={selectedVersion}
                                    choices={availableVersions}
                                />
                            {:else}
                                {"loading"}
                            {/if}
                        </div>
                    </Row>
                </div>
            </TitledContainer>
        </Row>
        {#await choosePromise}
            <Warning warning={tr.launcherSyncing()} className="alert-info" />
        {:then res}
            {#if res != null}
                <Warning
                    warning={tr.launcherAnkiWillStartShortly()}
                    className="alert-success"
                />
            {/if}
        {/await}
    {/key}
    <Row class="row-columns">
        <details bind:this={termTabRef}>
            {#key $currentLang}
                <summary>{tr.launcherOutput()}</summary>
            {/key}
            <div id="terminal" bind:this={termRef}></div>
        </details>
    </Row>
    {#key $currentLang}
        <Row class="row-columns">
            <TitledContainer title={tr.launcherAdvanced()}>
                <div class="m-2">
                    <SwitchRow
                        bind:value={allowBetas}
                        defaultValue={allowBetas}
                        hideRevert
                    >
                        <SettingTitle>
                            {tr.launcherAllowBetasToggle()}
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
                            {tr.launcherDownloadCaching()}
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
                            {tr.launcherUseMirror()}
                        </SettingTitle>
                    </EnumSelectorRow>
                </div>
            </TitledContainer>
        </Row>
    {/key}
</Container>

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

    .group {
        margin-top: 1em;
    }

    #terminal {
        width: 100%;
    }
</style>
