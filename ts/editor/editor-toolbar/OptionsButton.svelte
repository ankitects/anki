<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { bridgeCommand } from "@tslib/bridgecommand";

    import CheckBox from "$lib/components/CheckBox.svelte";
    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { cogIcon } from "$lib/components/icons";
    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";

    import { mathjaxConfig } from "../../editable/mathjax-element.svelte";
    import { shrinkImagesByDefault } from "../image-overlay/ImageOverlay.svelte";
    import { closeHTMLTags } from "../plain-text-input/PlainTextInput.svelte";

    let showFloating = false;

    function toggleShrinkImages(_evt: MouseEvent): void {
        $shrinkImagesByDefault = !$shrinkImagesByDefault;
        bridgeCommand("toggleShrinkImages");
        showFloating = false;
    }

    function toggleShowMathjax(_evt: MouseEvent): void {
        mathjaxConfig.enabled = !mathjaxConfig.enabled;
        bridgeCommand("toggleMathjax");
    }

    function toggleCloseHTMLTags(_evt: MouseEvent): void {
        $closeHTMLTags = !$closeHTMLTags;
        bridgeCommand("toggleCloseHTMLTags");
        showFloating = false;
    }
</script>

<WithFloating show={showFloating} inline on:close={() => (showFloating = false)}>
    <IconButton
        slot="reference"
        tooltip={tr.actionsOptions()}
        --border-left-radius="5px"
        --border-right-radius="5px"
        --padding-inline="8px"
        on:click={() => (showFloating = !showFloating)}
    >
        <Icon icon={cogIcon} />
    </IconButton>

    <Popover slot="floating" --popover-padding-inline="0">
        <DropdownItem on:click={toggleShrinkImages}>
            <CheckBox value={$shrinkImagesByDefault} />
            <span class="d-flex-inline ps-3">{tr.editingShrinkImages()}</span>
        </DropdownItem>
        <DropdownItem on:click={toggleShowMathjax}>
            <CheckBox value={mathjaxConfig.enabled} />
            <span class="d-flex-inline ps-3">{tr.editingMathjaxPreview()}</span>
        </DropdownItem>
        <DropdownItem on:click={toggleCloseHTMLTags}>
            <CheckBox value={$closeHTMLTags} />
            <span class="d-flex-inline ps-3">{tr.editingCloseHtmlTags()}</span>
        </DropdownItem>
    </Popover>
</WithFloating>
