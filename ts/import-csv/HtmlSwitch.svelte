<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Switch from "../components/Switch.svelte";

    export let isHtml: boolean;
    export let disabled: boolean;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingAllowHtmlInFields()}
    </Col>
    <Col --col-size={1} --col-justify="flex-end">
        <Switch id={undefined} bind:value={isHtml} {disabled} />
    </Col>
</Row>
