<script lang="typescript">
    import type { PreviewerInput } from "./utils";

    import { PreviewerMode } from "./utils";
    import CardDisplay from "./CardDisplay.svelte";
    import Navbar from "./Navbar.svelte";

    import CardTypeSelect from "./CardTypeSelect.svelte";
    import ClozeSelect from "./ClozeSelect.svelte";
    import BrowserSelect from "./BrowserSelect.svelte";

    export let cards: [string, string][] = [["", ""]];
    export let mode: PreviewerMode | null = null;
    export let cardTypeNames: string[] = [];

    let select = null;
    let currentCardType = 0;

    let showFront = true;
    let fillEmptyFields = false;
    let nightMode = false;
    let addMobileClass = false;

    let displayed = "";

    $: {
        switch (mode) {
            case PreviewerMode.StandardCards:
                select = CardTypeSelect;
                break;
            case PreviewerMode.ClozeCards:
                select = ClozeSelect;
                break;
        }
    }

    $: {
        cards;
        currentCardType;
        showFront;
        displayed = cards[currentCardType][showFront ? 0 : 1];
    }
</script>

<CardDisplay
    {displayed}
    {fillEmptyFields}
    {nightMode}
    {addMobileClass} />
<Navbar
    bind:showFront
    bind:fillEmptyFields
    bind:nightMode
    bind:addMobileClass
    extraControls={select}
    bind:currentCardType
    bind:cardTypeNames />
