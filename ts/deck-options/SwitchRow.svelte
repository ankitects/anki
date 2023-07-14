<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Switch from "../components/Switch.svelte";
    import ConfigInput from "./ConfigInput.svelte";
    import Label from "./Label.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: boolean;
    export let defaultValue: boolean;

    const id = Math.random().toString(36).substring(2);
</script>

<Row --cols={6}>
    <Col --col-size={4}><Label for={id} preventMouseClick><slot /></Label></Col>
    <Col --col-justify="flex-end">
        <ConfigInput grow={false}>
            <Switch {id} bind:value />
            <RevertButton slot="revert" bind:value {defaultValue} />
        </ConfigInput>
    </Col>
</Row>
