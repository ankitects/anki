<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Container from "$lib/components/Container.svelte";
    import LabelButton from "$lib/components/LabelButton.svelte";

    export let onPickImage: () => void;
    export let onPickImageFromClipboard: () => void;
</script>

<Container class="image-occlusion-picker">
    <div id="io-pick-image-file" style="padding-top: 60px; text-align: center;">
        <LabelButton
            tabbable={true}
            --border-left-radius="5px"
            --border-right-radius="5px"
            class="io-image-picker-button"
            on:click={onPickImage}
        >
            {tr.notetypesIoSelectImage()}
        </LabelButton>
    </div>
    <div id="io-pick-image-clipboard" style="padding-top: 30px; text-align: center;">
        <LabelButton
            tabbable={true}
            --border-left-radius="5px"
            --border-right-radius="5px"
            class="io-image-picker-button"
            on:click={onPickImageFromClipboard}
        >
            {tr.notetypesIoPasteImageFromClipboard()}
        </LabelButton>
    </div>
</Container>

<style lang="scss">
    :global(.io-image-picker-button) {
        margin: auto;
        padding: 0px 8px 0px 8px !important;
    }
</style>
