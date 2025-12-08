<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { type GetLangsResponse_Pair } from "@generated/anki/launcher_pb";
    import Row from "$lib/components/Row.svelte";
    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import Container from "$lib/components/Container.svelte";
    import { tr } from "./stores";

    let {
        langs,
        selectedLang = $bindable(),
        children,
        footer,
    }: {
        langs: GetLangsResponse_Pair[];
        selectedLang: string;
        children: any;
        footer: any;
    } = $props();

    const availableLangs = $derived(
        langs.map((p) => ({ label: p.name, value: p.locale })),
    );
</script>

<Container
    breakpoint="sm"
    --gutter-inline="0.25rem"
    --gutter-block="0.75rem"
    class="container-columns"
>
    <Row class="row-columns">
        <TitledContainer>
            <Row --cols={2} slot="title" class="title">
                <img src="/anki.png" alt="logo" class="logo" />
                <h1 class="title">{$tr.launcherTitle()}</h1>
            </Row>
            <EnumSelectorRow
                breakpoint="sm"
                bind:value={selectedLang}
                choices={availableLangs}
                defaultValue={selectedLang}
                hideRevert
            >
                <SettingTitle>
                    {$tr.launcherLanguage()}
                </SettingTitle>
            </EnumSelectorRow>

            {@render children?.()}
        </TitledContainer>
    </Row>

    {@render footer?.()}
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
</style>
