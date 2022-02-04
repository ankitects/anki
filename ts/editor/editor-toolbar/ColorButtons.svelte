<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import ColorPicker from "../../components/ColorPicker.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { execCommand } from "../helpers";
    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import { arrowIcon, highlightColorIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    export let api = {};
    export let textColor: string;
    export let highlightColor: string;

    const forecolorKeyCombination = "F7";
    $: forecolorWrap = wrapWithForecolor(textColor);

    const backcolorKeyCombination = "F8";
    $: backcolorWrap = wrapWithBackcolor(highlightColor);

    const wrapWithForecolor = (color: string) => () => {
        execCommand("forecolor", false, color);
    };

    const wrapWithBackcolor = (color: string) => () => {
        execCommand("backcolor", false, color);
    };

    const { focusedInput } = context.get();
    $: disabled = !editingInputIsRichText($focusedInput);
</script>

<ButtonGroup>
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
        <WithColorHelper color={textColor} let:colorHelperIcon let:setColor>
            <ButtonGroupItem>
                <IconButton
                    tooltip="{tr.editingSetTextColor()} ({getPlatformString(
                        forecolorKeyCombination,
                    )})"
                    {disabled}
                    on:click={forecolorWrap}
                >
                    {@html textColorIcon}
                    {@html colorHelperIcon}
                </IconButton>
                <Shortcut
                    keyCombination={forecolorKeyCombination}
                    on:action={forecolorWrap}
                />
            </ButtonGroupItem>

            <ButtonGroupItem>
                <IconButton
                    tooltip="{tr.editingChangeColor()} ({getPlatformString(
                        backcolorKeyCombination,
                    )})"
                    {disabled}
                    widthMultiplier={0.5}
                >
                    {@html arrowIcon}
                    <ColorPicker
                        on:change={(event) => {
                            const textColor = setColor(event);
                            bridgeCommand(`lastTextColor:${textColor}`);
                            forecolorWrap = wrapWithForecolor(setColor(event));
                            forecolorWrap();
                        }}
                    />
                </IconButton>
                <Shortcut
                    keyCombination={backcolorKeyCombination}
                    on:action={(event) => {
                        const textColor = setColor(event);
                        bridgeCommand(`lastTextColor:${textColor}`);
                        forecolorWrap = wrapWithForecolor(setColor(event));
                        forecolorWrap();
                    }}
                />
            </ButtonGroupItem>
        </WithColorHelper>

        <WithColorHelper color={highlightColor} let:colorHelperIcon let:setColor>
            <ButtonGroupItem>
                <IconButton
                    tooltip={tr.editingSetTextHighlightColor()}
                    {disabled}
                    on:click={backcolorWrap}
                >
                    {@html highlightColorIcon}
                    {@html colorHelperIcon}
                </IconButton>
            </ButtonGroupItem>

            <ButtonGroupItem>
                <IconButton
                    tooltip={tr.editingChangeColor()}
                    widthMultiplier={0.5}
                    {disabled}
                >
                    {@html arrowIcon}
                    <ColorPicker
                        on:change={(event) => {
                            const highlightColor = setColor(event);
                            bridgeCommand(`lastHighlightColor:${highlightColor}`);
                            backcolorWrap = wrapWithBackcolor(highlightColor);
                            backcolorWrap();
                        }}
                    />
                </IconButton>
            </ButtonGroupItem>
        </WithColorHelper>
    </DynamicallySlottable>
</ButtonGroup>
