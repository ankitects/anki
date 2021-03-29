<script lang="typescript">
    import ButtonBarOuter from "./ButtonBarOuter.svelte";
    import ButtonBar from "./ButtonBar.svelte";
    import ButtonGroup from "./ButtonGroup.svelte";

    import LabelButton from "./LabelButton.svelte";
    import IconButton from "./IconButton.svelte";

    import bracketsIcon from "./code-brackets.svg";

    import paperclipIcon from "./paperclip.svg";
    import micIcon from "./mic.svg";
    import threeDotsIcon from "./three-dots.svg";

    import {
        boldButton,
        italicButton,
        underlineButton,
        superscriptButton,
        subscriptButton,
        eraserButton,
    } from "./format";
    import { forecolorButton, colorpickerButton } from "./color";

    export let leftButtons = [
        [
            { component: LabelButton, label: "Fields..." },
            { component: LabelButton, label: "Cards..." },
        ],
    ];

    export let rightButtons = [
        [
            boldButton,
            italicButton,
            underlineButton,
            superscriptButton,
            subscriptButton,
            eraserButton,
        ],
        [forecolorButton, colorpickerButton],
        [
            { component: IconButton, icon: bracketsIcon },
            { component: IconButton, icon: paperclipIcon },
            { component: IconButton, icon: micIcon },
            { component: IconButton, icon: threeDotsIcon },
        ],
    ];
    export let nightMode: boolean;

    console.log(nightMode);
</script>

<ButtonBarOuter>
    <ButtonBar>
        {#each leftButtons as leftGroup}
            <ButtonGroup>
                {#each leftGroup as leftButton}
                    <svelte:component this={leftButton.component} {...leftButton} />
                {/each}
            </ButtonGroup>
        {/each}
    </ButtonBar>

    <ButtonBar>
        {#each rightButtons as rightGroup}
            <ButtonGroup>
                {#each rightGroup as rightButton}
                    <svelte:component this={rightButton.component} {...rightButton} />
                {/each}
            </ButtonGroup>
        {/each}
    </ButtonBar>
</ButtonBarOuter>
