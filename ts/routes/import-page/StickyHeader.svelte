<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import ButtonToolbar from "$lib/components/ButtonToolbar.svelte";
    import LabelButton from "$lib/components/LabelButton.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import StickyContainer from "$lib/components/StickyContainer.svelte";

    export let path: string;
    export let onImport: () => void;

    const keyCombination = "Control+Enter";

    function basename(path: String): String {
        return path.split(/[\\/]/).pop()!;
    }
</script>

<StickyContainer
    --gutter-block="0.5rem"
    --gutter-inline="0.25rem"
    --sticky-borders="0 0 1px"
    breakpoint="sm"
>
    <ButtonToolbar class="justify-content-between" wrap={false}>
        <div class="filename">{basename(path)}</div>
        <LabelButton
            primary
            tooltip={getPlatformString(keyCombination)}
            on:click={onImport}
            --border-left-radius="5px"
            --border-right-radius="5px"
        >
            <div class="import">{tr.actionsImport()}</div>
        </LabelButton>
        <Shortcut {keyCombination} on:action={onImport} />
    </ButtonToolbar>
</StickyContainer>

<style lang="scss">
    .import {
        margin: 0.2rem 0.75rem;
    }

    .filename {
        word-break: break-word;
    }
</style>
