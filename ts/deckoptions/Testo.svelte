<!--
    import { slide } from "svelte/transition";
    import RevertButton from "./RevertButton.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import marked from "marked";

    export let label: string;
    export let tooltip = "";
    export let value: any;
    export let defaultValue: any;
    /// empty strings will be ignored
    export let warnings: string[] = [];
    export let wholeLine = false;

    let renderedTooltip: string;
    $: renderedTooltip = marked(tooltip);


/* {label}{#if renderedTooltip}<HelpPopup html={renderedTooltip} />{/if} */

/* <slot /> */
/* <RevertButton bind:value {defaultValue} on:revert /> */


-->
