<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import LabelButton from "$lib/components/LabelButton.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import StickyContainer from "$lib/components/StickyContainer.svelte";

    export let onAccept: () => Promise<void>;

    const keyCombination = "Control+Enter";
</script>

<StickyContainer
    --gutter-block="0.5rem"
    --gutter-inline="0.25rem"
    --sticky-borders="0 0 1px"
    breakpoint="sm"
>
    <div class="d-flex flex-row justify-content-end w-100">
        <div class="accept">
            <!-- svelte-ignore missing-declaration -->
            <LabelButton
                primary
                tooltip={getPlatformString(keyCombination)}
                on:click={onAccept}
                --border-left-radius="5px"
                --border-right-radius="5px"
            >
                <div class="export-label">{tr.actionsExport()}</div>
            </LabelButton>
            <Shortcut {keyCombination} on:action={onAccept} />
        </div>
    </div>
</StickyContainer>

<style lang="scss">
    .export-label {
        margin: 0.2rem 0.75rem;
    }
</style>
