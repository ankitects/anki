<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Col from "$lib/components/Col.svelte";
    import Container from "$lib/components/Container.svelte";
    import Row from "$lib/components/Row.svelte";

    import Alert from "./Alert.svelte";
    import type { ChangeNotetypeState } from "./lib";
    import { MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    $: info = state.info;

    // svelte-ignore reactive_declaration_non_reactive_property
    $: unused =
        $info.isCloze && ctx === MapContext.Template ? [] : $info.unusedItems(ctx);
</script>

{#if unused.length > 0}
    <Alert {unused} {ctx} />
{/if}

{#if $info.templates || ctx === MapContext.Field}
    <Container --gutter-inline="0.5rem" --gutter-block="0.2rem">
        <Row --cols={2}>
            <Col --col-size={1}><b>{tr.changeNotetypeCurrent()}</b></Col>
            <Col --col-size={1}><b>{tr.changeNotetypeNew()}</b></Col>
        </Row>
    </Container>
{/if}
