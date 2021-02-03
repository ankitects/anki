<script lang="typescript">
    import type { SvelteComponent } from "svelte/internal";
    import FrontBackButtons from "./FrontBackButtons.svelte";
    import OptionsDropup from "./OptionsDropup.svelte";

    export let extraControls: SvelteComponent;
    export let cardTypeNames: string[];
    export let currentCardType: number;

    export let showFront: boolean;
    export let fillEmptyFields: boolean;
    export let nightMode: boolean;
    export let addMobileClass: boolean;
</script>

<nav class="navbar navbar-light bg-light fixed-bottom">
    <div class="w-100 mx-2 d-flex justify-content-between">
        <!-- left buttons -->
        <FrontBackButtons bind:showFront />

        <!-- right buttons -->
        <div class="d-flex">
            <svelte:component
                this={extraControls}
                {cardTypeNames}
                bind:currentCardType />
            <OptionsDropup bind:fillEmptyFields bind:nightMode bind:addMobileClass />
        </div>
    </div>
</nav>
