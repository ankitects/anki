<script lang="typescript">
    import ButtonBarOuter from "./ButtonBarOuter.svelte";
    import ButtonBar from "./ButtonBar.svelte";

    import LabelButton from "./LabelButton.svelte";
    import IconButton from "./IconButton.svelte";

    import boldIcon from "./type-bold.svg";
    import italicIcon from "./type-italic.svg";
    import underlineIcon from "./type-underline.svg";

    export let leftButtons = [
        { component: LabelButton, label: "Fields..." },
        { component: LabelButton, label: "Cards..." },
    ];

    export let rightButtons = [
        { component: IconButton, icon: boldIcon },
        { component: IconButton, icon: italicIcon },
        { component: IconButton, icon: underlineIcon },
    ];
    export let nightMode: boolean;

    console.log(nightMode);
</script>

<ButtonBarOuter>
    <ButtonBar>
        {#each leftButtons as leftButton}
            <svelte:component this={leftButton.component} {...leftButton} />
        {/each}
    </ButtonBar>

    <ButtonBar>
        {#each rightButtons as rightButton}
            <svelte:component this={rightButton.component} {...rightButton} />
        {/each}
    </ButtonBar>
</ButtonBarOuter>
