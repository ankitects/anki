<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { EditingArea } from "./editingArea";
    import * as tr from "lib/i18n";

    import IconButton from "components/IconButton.svelte";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonDropdown from "components/ButtonDropdown.svelte";
    import WithState from "components/WithState.svelte";
    import WithDropdownMenu from "components/WithDropdownMenu.svelte";

    import { getListItem } from "./helpers";
    import {
        ulIcon,
        olIcon,
        listOptionsIcon,
        justifyFullIcon,
        justifyLeftIcon,
        justifyRightIcon,
        justifyCenterIcon,
        indentIcon,
        outdentIcon,
    } from "./icons";

    export let api = {};

    function outdentListItem() {
        const currentField = document.activeElement as EditingArea;
        if (getListItem(currentField.shadowRoot!)) {
            document.execCommand("outdent");
        }
    }

    function indentListItem() {
        const currentField = document.activeElement as EditingArea;
        if (getListItem(currentField.shadowRoot!)) {
            document.execCommand("indent");
        }
    }
</script>

<ButtonGroup id="blockFormatting" {api}>
    <WithState
        key="insertUnorderedList"
        update={() => document.queryCommandState('insertUnorderedList')}
        let:state={active}
        let:updateState>
        <IconButton
            tooltip={tr.editingUnorderedList()}
            {active}
            on:click={(event) => {
                document.execCommand('insertUnorderedList');
                updateState(event);
            }}>
            {@html ulIcon}
        </IconButton>
    </WithState>

    <WithState
        key="insertOrderedList"
        update={() => document.queryCommandState('insertOrderedList')}
        let:state={active}
        let:updateState>
        <IconButton
            tooltip={tr.editingOrderedList()}
            {active}
            on:click={(event) => {
                document.execCommand('insertOrderedList');
                updateState(event);
            }}>
            {@html olIcon}
        </IconButton>
    </WithState>

    <WithDropdownMenu let:createDropdown let:menuId>
        <IconButton on:mount={(event) => createDropdown(event.detail.button)}>
            {@html listOptionsIcon}
        </IconButton>

        <ButtonDropdown id={menuId}>
            <ButtonGroup id="justify" {api}>
                <WithState
                    key="justifyLeft"
                    update={() => document.queryCommandState('justifyLeft')}
                    let:state={active}
                    let:updateState>
                    <IconButton
                        tooltip={tr.editingAlignLeft()}
                        {active}
                        on:click={(event) => {
                            document.execCommand('justifyLeft');
                            updateState(event);
                        }}>
                        {@html justifyLeftIcon}
                    </IconButton>
                </WithState>

                <WithState
                    key="justifyCenter"
                    update={() => document.queryCommandState('justifyCenter')}
                    let:state={active}
                    let:updateState>
                    <IconButton
                        tooltip={tr.editingCenter()}
                        {active}
                        on:click={(event) => {
                            document.execCommand('justifyCenter');
                            updateState(event);
                        }}>
                        {@html justifyCenterIcon}
                    </IconButton>
                </WithState>

                <WithState
                    key="justifyRight"
                    update={() => document.queryCommandState('justifyRight')}
                    let:state={active}
                    let:updateState>
                    <IconButton
                        tooltip={tr.editingAlignRight()}
                        {active}
                        on:click={(event) => {
                            document.execCommand('justifyRight');
                            updateState(event);
                        }}>
                        {@html justifyRightIcon}
                    </IconButton>
                </WithState>

                <WithState
                    key="justifyFull"
                    update={() => document.queryCommandState('justifyFull')}
                    let:state={active}
                    let:updateState>
                    <IconButton
                        tooltip={tr.editingJustify()}
                        {active}
                        on:click={(event) => {
                            document.execCommand('justifyFull');
                            updateState(event);
                        }}>
                        {@html justifyFullIcon}
                    </IconButton>
                </WithState>
            </ButtonGroup>

            <ButtonGroup id="indentation" {api}>
                <IconButton on:click={outdentListItem} tooltip={tr.editingOutdent}>
                    {@html outdentIcon}
                </IconButton>

                <IconButton on:click={indentListItem} tooltip={tr.editingIndent}>
                    {@html indentIcon}
                </IconButton>
            </ButtonGroup>
        </ButtonDropdown>
    </WithDropdownMenu>
</ButtonGroup>
