<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import LabelButton from "$lib/components/LabelButton.svelte";

    import type { IOMode } from "./lib";

    export let mode: IOMode;
    export let addNote: () => void;
</script>

<div style:flex-grow="1"></div>
<div class="sticky-footer">
    <ButtonGroup size={2}>
        <LabelButton
            --border-left-radius="5px"
            --border-right-radius="5px"
            on:click={addNote}
            class=" bottom-btn"
        >
            {mode.kind === "add" ? tr.actionsAdd() : tr.importingUpdate()}
        </LabelButton>
    </ButtonGroup>
</div>

<style lang="scss">
    .sticky-footer {
        position: fixed;
        bottom: 0;
        left: 0;
        right: 0;
        z-index: 99;
        margin: 0;
        padding: 0.25rem;
        background: var(--canvas);
        border-style: solid none none;
        border-color: var(--border);
        border-width: thin;
        display: flex;
        justify-content: flex-end;
    }

    @media only screen and (max-width: 640px) {
        .sticky-footer {
            justify-content: center;
        }
    }

    :global(.bottom-btn) {
        margin: 2px;
    }
</style>
