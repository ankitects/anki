<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import Container from "$lib/components/Container.svelte";
    import LabelButton from "$lib/components/LabelButton.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";

    export let onAccept: () => Promise<void>;

    const keyCombination = "Control+Enter";
</script>

<div class="sticky-header">
    <Container
        breakpoint="sm"
        --gutter-inline="0.25rem"
        --gutter-block="0.75rem"
        class="container-columns"
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
    </Container>
</div>

<style lang="scss">
    .sticky-header {
        position: sticky;
        top: 0;
        left: 0;
        right: 0;
        z-index: 10;

        margin: 0;

        background: var(--canvas);
        border-bottom: 1px solid var(--border);

        .export-label {
            margin: 0.5em 1.5em;
        }
    }
</style>
