<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import marked from "marked";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import StepsInput from "./StepsInput.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: any;
    export let defaultValue: any;
    export let markdownTooltip: string;
</script>

<Row>
    <Col size={7}>
        <slot /><HelpPopup html={marked(markdownTooltip)} />
    </Col>
    <Col size={5}>
        <StepsInput bind:value />
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
