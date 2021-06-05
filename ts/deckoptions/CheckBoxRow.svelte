<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import marked from "marked";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import WithTooltip from "./WithTooltip.svelte";
    import Label from "./Label.svelte";
    import CheckBox from "./CheckBox.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: boolean;
    export let defaultValue: boolean;
    export let markdownTooltip: string | undefined = undefined;
</script>

<Row>
    <Col>
        <CheckBox bind:value
            >{#if markdownTooltip}<WithTooltip
                    tooltip={marked(markdownTooltip)}
                    let:createTooltip
                >
                    <Label on:mount={(event) => createTooltip(event.detail.span)}
                        ><slot /></Label
                    >
                </WithTooltip>{:else}<Label><slot /></Label>{/if}
        </CheckBox>
    </Col>
    <Col grow={false}>
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
