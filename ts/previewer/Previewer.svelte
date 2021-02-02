<script lang="typescript">
    import type { PreviewerInput } from "./utils";

    import { PreviewerMode } from "./utils";
    import CardDisplay from "./CardDisplay.svelte";
    import Navbar from "./Navbar.svelte";

    import CardTypeSelect from "./CardTypeSelect.svelte";
    import ClozeSelect from "./ClozeSelect.svelte";
    import BrowserSelect from "./BrowserSelect.svelte";

    export let input: PreviewerInput = [];
    export let mode: PreviewerMode | null;
    export let cardTypeNames: string[] = [];

    let select = null;
    let currentCardNumber = 0;

    let showFront = true;
    let fillEmptyFields = false;
    let nightMode = false;
    let addMobileClass = false;

    $: {
        switch (mode) {
            case PreviewerMode.StandardCards:
                select = CardTypeSelect;
        }
    }
</script>

<CardDisplay {input} />
<Navbar
    bind:showFront
    bind:fillEmptyFields
    bind:nightMode
    bind:addMobileClass
    extraControls={select}
    bind:currentCardNumber
    bind:cardTypeNames />
