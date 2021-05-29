<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import marked from "marked";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import CheckBox from "./CheckBox.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: boolean;
    export let defaultValue: boolean;
    export let markdownTooltip: string | undefined = undefined;
</script>

<Row>
    <Col>
        <CheckBox bind:value
            ><slot />
            {#if markdownTooltip}<HelpPopup
                    html={marked(markdownTooltip)}
                />{/if}</CheckBox
        >
    </Col>
    <Col grow={false}>
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
