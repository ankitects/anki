<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import CheckBox from "../../components/CheckBox.svelte";
    import DropdownItem from "../../components/DropdownItem.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Popover from "../../components/Popover.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { shrinkImagesByDefault } from "../image-overlay/ImageOverlay.svelte";
    import { cogIcon } from "./icons";

    let showFloating = false;

    function toggleShrinkImages(_evt: MouseEvent): void {
        $shrinkImagesByDefault = !$shrinkImagesByDefault;
        bridgeCommand("toggleShrinkImages");
        showFloating = false;
    }
</script>

<WithFloating
    show={showFloating}
    placement="bottom"
    inline
    on:close={() => (showFloating = false)}
    let:asReference
>
    <span use:asReference>
        <IconButton
            tooltip={tr.actionsOptions()}
            --border-left-radius="5px"
            --border-right-radius="5px"
            on:click={() => (showFloating = !showFloating)}
        >
            {@html cogIcon}
        </IconButton>
    </span>

    <Popover slot="floating" --popover-padding-inline="0">
        <DropdownItem on:click={toggleShrinkImages}>
            <CheckBox value={$shrinkImagesByDefault} />
            <span class="d-flex-inline ps-3">{tr.editingShrinkImages()}</span>
        </DropdownItem>
    </Popover>
</WithFloating>
