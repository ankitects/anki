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

    const { data }: PageProps = $props();

    const langs = data.langs;
    const options = $state(data.options);
    let mirrors = $state(data.mirrors);
    let selectedLang = $state(data.userLocale);

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
</script>

<Start bind:selectedLang {langs} {options} {mirrors} />
