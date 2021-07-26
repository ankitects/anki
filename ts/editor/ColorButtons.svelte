<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as tr from "lib/i18n";

    import ButtonGroup from "components/ButtonGroup.svelte";
    import ButtonGroupItem from "components/ButtonGroupItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import ColorPicker from "components/ColorPicker.svelte";
    import WithShortcut from "components/WithShortcut.svelte";
    import WithColorHelper from "./WithColorHelper.svelte";
    import OnlyEditable from "./OnlyEditable.svelte";

    import { textColorIcon, highlightColorIcon, arrowIcon } from "./icons";
    import { appendInParentheses } from "./helpers";

    export let api = {};

    const wrapWithForecolor = (color: string) => () => {
        document.execCommand("forecolor", false, color);
    };

    const wrapWithBackcolor = (color: string) => () => {
        document.execCommand("backcolor", false, color);
    };

    const initialColor = "black";

    let forecolorWrap = wrapWithForecolor(initialColor);
    let backcolorWrap = wrapWithForecolor(initialColor);
</script>

<ButtonGroup {api}>
    <WithColorHelper color={initialColor} let:colorHelperIcon let:setColor>
        <OnlyEditable let:disabled>
            <ButtonGroupItem>
                <WithShortcut shortcut={"F7"} let:createShortcut let:shortcutLabel>
                    <IconButton
                        tooltip={appendInParentheses(
                            tr.editingSetTextColor(),
                            shortcutLabel
                        )}
                        {disabled}
                        on:click={forecolorWrap}
                        on:mount={(event) => createShortcut(event.detail.button)}
                    >
                        {@html textColorIcon}
                        {@html colorHelperIcon}
                    </IconButton>
                </WithShortcut>
            </ButtonGroupItem>

            <ButtonGroupItem>
                <WithShortcut shortcut={"F8"} let:createShortcut let:shortcutLabel>
                    <IconButton
                        tooltip={appendInParentheses(
                            tr.editingChangeColor(),
                            shortcutLabel
                        )}
                        {disabled}
                        widthMultiplier={0.5}
                    >
                        {@html arrowIcon}
                        <ColorPicker
                            on:change={(event) => {
                                forecolorWrap = wrapWithForecolor(setColor(event));
                                forecolorWrap();
                            }}
                            on:mount={(event) => createShortcut(event.detail.input)}
                        />
                    </IconButton>
                </WithShortcut>
            </ButtonGroupItem>
        </OnlyEditable>
    </WithColorHelper>

    <WithColorHelper color={initialColor} let:colorHelperIcon let:setColor>
        <OnlyEditable let:disabled>
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
                            backcolorWrap = wrapWithBackcolor(setColor(event));
                            backcolorWrap();
                        }}
                    />
                </IconButton>
            </ButtonGroupItem>
        </OnlyEditable>
    </WithColorHelper>
</ButtonGroup>
