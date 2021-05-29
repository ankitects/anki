<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Breakpoint } from "Col.svelte";

    import marked from "marked";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: number;
    export let defaultValue: number;
    export let breakpoint: Breakpoint = "md";
    export let choices: string[];
    export let markdownTooltip: string;
</script>

<Row>
    <Col size={7}>
        <slot /><HelpPopup html={marked(markdownTooltip)} />
    </Col>
    <Col {breakpoint} size={5}>
        <EnumSelector bind:value {choices} />
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
