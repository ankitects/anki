<script lang="typescript">
    import ButtonBarOuter from "./ButtonBarOuter.svelte";
    import ButtonBar from "./ButtonBar.svelte";

    import LabelButton from "./LabelButton.svelte";
    import IconButton from "./IconButton.svelte";

    import boldIcon from "./type-bold.svg";
    import italicIcon from "./type-italic.svg";
    import underlineIcon from "./type-underline.svg";

    import superscriptIcon from "./format-superscript.svg";
    import subscriptIcon from "./format-subscript.svg";
    import bracketsIcon from "./code-brackets.svg";

    import eraserIcon from "./eraser.svg";
    import squareFillIcon from "./square-fill.svg";
    import paperclipIcon from "./paperclip.svg";
    import micIcon from "./mic.svg";
    import threeDotsIcon from "./three-dots.svg";

    export let leftButtons = [
        { component: LabelButton, label: "Fields..." },
        { component: LabelButton, label: "Cards..." },
    ];

    export let rightButtons = [
        { component: IconButton, icon: boldIcon },
        { component: IconButton, icon: italicIcon },
        { component: IconButton, icon: underlineIcon },

        { component: IconButton, icon: superscriptIcon },
        { component: IconButton, icon: subscriptIcon },

        { component: IconButton, icon: eraserIcon },
        { component: IconButton, icon: squareFillIcon },
        { component: IconButton, icon: squareFillIcon },

        { component: IconButton, icon: bracketsIcon },
        { component: IconButton, icon: paperclipIcon },
        { component: IconButton, icon: micIcon },
        { component: IconButton, icon: threeDotsIcon },
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
