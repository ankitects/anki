<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import Container from "../components/Container.svelte";
    import LabelButton from "components/LabelButton.svelte";

    export let onPickImage: () => void;
    export let onPickImageFromClipboard: () => void;
</script>

<Container class="image-occlusion-picker">
    <div id="io-select-image-div" style="padding-top: 60px; text-align: center;">
        <LabelButton
            --border-left-radius="5px"
            --border-right-radius="5px"
            class="io-select-image-btn"
            on:click={onPickImage}
        >
            {tr.notetypesIoSelectImage()}
        </LabelButton>
    </div>
    <div
        id="io-select-clipboard-image-div"
        style="padding-top: 30px; text-align: center;"
    >
        <LabelButton
            --border-left-radius="5px"
            --border-right-radius="5px"
            class="io-select-image-btn"
            on:click={onPickImageFromClipboard}
        >
            {tr.notetypesIoPasteImageFromClipboard()}
        </LabelButton>
    </div>
</Container>
