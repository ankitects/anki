<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts" module>
</script>

<script lang="ts">
    import type { PageProps } from "./$types";
    import * as _tr from "@generated/ftl-launcher";
    import { setLang, windowReady, zoomWebview } from "@generated/backend-launcher";
    import { getMirrors } from "@generated/backend-launcher";
    import { ModuleName, setupI18n } from "@tslib/i18n";
    import { onMount } from "svelte";
    import { tr, zoomFactor } from "./stores";
    import Start from "./Start.svelte";
    import ErrorState from "./ErrorState.svelte";
    import Normal from "./Normal.svelte";
    import Uninstall from "./Uninstall.svelte";

    const { data }: PageProps = $props();

    let langs = $state(data.langs);
    let selectedLang = $state(data.userLocale);
    let flow = $state(data.state);
    let mirrors = $state(data.mirrors);
    let uninstallInfo = data.uninstallInfo;

    async function onLangChange(lang: string) {
        await setLang({ val: lang });
        await setupI18n({ modules: [ModuleName.LAUNCHER] }, true);

        $tr = _tr;
        mirrors = (await getMirrors({})).mirrors;
    }

    $effect(() => {
        onLangChange(selectedLang);
    });

    $effect(() => {
        zoomWebview({ scaleFactor: $zoomFactor });
    });

    onMount(() => windowReady({}));

    let footer: any = $state(null);

    const uninstall = uninstallInfo.ankiProgramFilesExists
        ? () => {
              flow.case = "uninstall";
          }
        : null;
</script>

<Start bind:selectedLang {langs} {footer}>
    {#if flow.case === "normal"}
        <Normal {mirrors} options={flow.value.options!} {uninstall} bind:footer />
    {:else if flow.case === "uninstall"}
        <Uninstall {uninstallInfo} bind:footer />
    {:else if flow.case === "osUnsupported"}
        <ErrorState
            title={$tr.launcherOsUnsupported()}
            detail={flow.value}
            bind:footer
        />
    {:else if flow.case === "unknownError"}
        <ErrorState
            title={$tr.launcherUnknownError()}
            detail={flow.value}
            bind:footer
        />
    {/if}
</Start>
