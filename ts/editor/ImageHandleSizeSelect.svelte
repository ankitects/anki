<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";

    import { createEventDispatcher } from "svelte";
    import { sizeActual, sizeMinimized } from "./icons";

    export let image: HTMLImageElement;
    export let imageRule: CSSStyleRule;

    const selector = `:not([src="${image.getAttribute("src")}"])`;

    export let active = imageRule.selectorText.includes(selector);

    $: icon = active ? sizeActual : sizeMinimized;

    const dispatch = createEventDispatcher();

    function toggleActualSize() {
        if (!image.hasAttribute("src")) {
            return;
        }

        if (active) {
            imageRule.selectorText = imageRule.selectorText.replace(selector, "");
            active = false;
        } else {
            imageRule.selectorText += selector;
            active = true;
        }

        dispatch("update");
    }
</script>

<ButtonGroup size={1.6}>
    <ButtonGroupItem>
        <IconButton
            {active}
            tooltip={tr.editingActualSize()}
            on:click={toggleActualSize}>{@html icon}</IconButton
        >
    </ButtonGroupItem>
</ButtonGroup>
