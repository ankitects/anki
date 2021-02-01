<script lang="typescript">
    import FrontBackButtons from "./FrontBackButtons.svelte";
    import OptionsDropup from "./OptionsDropup.svelte";

    let showFront = true;
    let fillEmptyFields = false;
    let nightMode = false;
    let addMobileClass = false;
</script>

<nav class="navbar navbar-light bg-light fixed-bottom">
    <div class="w-100 mx-2 d-flex justify-content-between">
        <!-- left buttons -->
        <FrontBackButtons bind:showFront />

        <!-- right buttons -->
        <div>
            <OptionsDropup
                bind:fillEmptyFields
                bind:nightMode
                bind:addMobileClass
            />
        </div>
    </div>
</nav>
