<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";

    import ButtonGroup from "../components/ButtonGroup.svelte";
    import Col from "../components/Col.svelte";
    import LabelButton from "../components/LabelButton.svelte";
    import Row from "../components/Row.svelte";
    import Shortcut from "../components/Shortcut.svelte";

    export let onImport: () => void;

    const keyCombination = "Control+Enter";
</script>

<div class="footer">
    <Row class="align-items-center" --gutter-block="0.5rem">
        <Col --col-justify="center">
            <ButtonGroup size={2}>
                <LabelButton
                    primary
                    tooltip={getPlatformString(keyCombination)}
                    on:click={onImport}
                    --border-left-radius="5px"
                    --border-right-radius="5px"
                    tabbable>{tr.actionsImport()}</LabelButton
                >
                <Shortcut {keyCombination} on:action={onImport} />
            </ButtonGroup>
        </Col>
    </Row>
</div>
