<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Alert from "./Alert.svelte";
    import type { ChangeNotetypeState } from "./lib";
    import type { MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    $: info = state.info;
    $: unused = $info.unusedItems(ctx);
</script>

{#if unused.length > 0}
    <Alert {unused} {ctx} />
{/if}

{#if $info.templates}
    <Row --cols={2}>
        <Col --col-size={1}><b>{tr.changeNotetypeCurrent()}</b></Col>
        <Col --col-size={1}><b>{tr.changeNotetypeNew()}</b></Col>
    </Row>
{/if}
