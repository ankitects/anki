<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import Col from "$lib/components/Col.svelte";
    import ConfigInput from "$lib/components/ConfigInput.svelte";
    import Row from "$lib/components/Row.svelte";

    export let date;
    $: date = date ? date : "1970-01-01";
</script>

<div>
    <ConfigInput>
        <Row --cols={13}>
            <Col --col-size={7} breakpoint="xs">
                <slot />
            </Col>
            <Col --col-size={6} breakpoint="xs">
                <input bind:value={date} type="date" />
            </Col>
        </Row>
    </ConfigInput>
</div>

<style>
    input {
        width: 100%;
        -webkit-appearance: none;
        appearance: none;
        background: var(--canvas-inset);
        border: 1px solid var(--border);
        border-radius: var(--border-radius);
        padding: 1px 0.5em;
        outline: none !important;
    }
</style>
