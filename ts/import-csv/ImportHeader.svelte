<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";

    export let path: string;

    function basename(path: String): String {
        return path.split(/[\\/]/).pop()!;
    }
</script>

<div class="header">
    <Row class="align-items-center">
        <Col --col-justify="center">{basename(path)}</Col>
    </Row>
</div>

<style lang="scss">
    .header {
        border-bottom: 1px solid var(--border);
    }
</style>
