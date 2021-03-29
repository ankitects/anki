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

    export let buttons = [
        [
            { component: LabelButton, label: "Fields..." },
            { component: LabelButton, label: "Cards..." },
        ],
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
        {#each buttons as group}
            <ButtonGroup>
                {#each group as button}
                    <svelte:component this={button.component} {...button} />
                {/each}
            </ButtonGroup>
        {/each}
    </ButtonBar>
</ButtonBarOuter>
