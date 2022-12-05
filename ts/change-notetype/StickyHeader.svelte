<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import Badge from "../components/Badge.svelte";
    import Col from "../components/Col.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import StickyContainer from "../components/StickyContainer.svelte";
    import Alert from "./Alert.svelte";
    import { exclamationIcon } from "./icons";
    import type { ChangeNotetypeState } from "./lib";
    import { MapContext } from "./lib";

    export let state: ChangeNotetypeState;
    export let ctx: MapContext;

    $: info = state.info;

    const heading: string =
        ctx === MapContext.Field
            ? tr.changeNotetypeFields()
            : tr.changeNotetypeTemplates();

    $: unused = $info.unusedItems(ctx);
</script>

<StickyContainer
    --sticky-top={ctx === MapContext.Template ? "-1px" : "0"}
    --sticky-border="var(--border)"
    --sticky-borders="0px 0 1px"
>
    <h1>
        {heading}
        {#if unused.length > 0}
            <Badge iconSize={80}>
                {@html exclamationIcon}
            </Badge>
        {/if}
    </h1>

    {#if unused.length > 0}
        <Alert {unused} {ctx} />
    {/if}

    {#if $info.templates}
        <Container --gutter-inline="0.5rem" --gutter-block="0.2rem">
            <Row --cols={2}>
                <Col --col-size={1}><b>{tr.changeNotetypeCurrent()}</b></Col>
                <Col --col-size={1}><b>{tr.changeNotetypeNew()}</b></Col>
            </Row>
        </Container>
    {/if}
</StickyContainer>

<style lang="scss">
    h1 {
        padding-top: 0.5em;
    }
</style>
