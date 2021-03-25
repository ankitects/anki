<script lang="typescript">
    import ButtonBarOuter from "./ButtonBarOuter.svelte";
    import ButtonBar from "./ButtonBar.svelte";

    import LeftButton from "./LeftButton.svelte";
    import RightButton from "./RightButton.svelte";

    export let leftButtons = ["Fields...", "Cards..."];
    export let rightButtons = ["bi bi-type-bold", "bi bi-pin-angle"];
    export let nightMode: boolean;

    console.log(nightMode);
</script>

<ButtonBarOuter>
    <ButtonBar>
        {#each leftButtons as leftButton}
            <LeftButton>{leftButton}</LeftButton>
        {/each}
    </ButtonBar>

    <ButtonBar>
        {#each rightButtons as rightButton}
            <RightButton className={rightButton} />
        {/each}
    </ButtonBar>
</ButtonBarOuter>
