<script>
    import ConfigInput from "components/ConfigInput.svelte";
    import Row from "components/Row.svelte";
    import Col from "components/Col.svelte";

    export let date;
</script>

<div>
    <ConfigInput>
        <Row --cols={2}>
            <Col>
                <slot />
            </Col>
            <Col>
                <input bind:value={date} type="date">
            </Col>
        </Row>
    </ConfigInput>
</div>

<style>
    input {
        width: 100%;
    }
</style>