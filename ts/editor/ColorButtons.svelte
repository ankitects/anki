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

    import { textColorIcon, highlightColorIcon, arrowIcon } from "./icons";
    import { appendInParentheses } from "./helpers";

    export let api = {};

    const wrapWithForecolor = (color: string) => () => {
        document.execCommand("forecolor", false, color);
    };

    const wrapWithBackcolor = (color: string) => () => {
        document.execCommand("backcolor", false, color);
    };
</script>

<ButtonGroup {api}>
    <WithColorHelper let:colorHelperIcon let:color let:setColor>
        <ButtonGroupItem>
            <WithShortcut shortcut={"F7"} let:createShortcut let:shortcutLabel>
                <IconButton
                    tooltip={appendInParentheses(
                        tr.editingSetForegroundColor(),
                        shortcutLabel
                    )}
                    on:click={wrapWithForecolor(color)}
                    on:mount={createShortcut}
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
                    disables={false}
                    widthMultiplier={0.5}
                >
                    {@html arrowIcon}
                    <ColorPicker on:change={setColor} on:mount={createShortcut} />
                </IconButton>
            </WithShortcut>
        </ButtonGroupItem>
    </WithColorHelper>

    <WithColorHelper let:colorHelperIcon let:color let:setColor>
        <ButtonGroupItem>
            <IconButton on:click={wrapWithBackcolor(color)}>
                {@html highlightColorIcon}
                {@html colorHelperIcon}
            </IconButton>
        </ButtonGroupItem>

        <ButtonGroupItem>
            <IconButton
                tooltip={tr.editingChangeColor()}
                disables={false}
                widthMultiplier={0.5}
            >
                {@html arrowIcon}
                <ColorPicker on:change={setColor} />
            </IconButton>
        </ButtonGroupItem>
    </WithColorHelper>
</ButtonGroup>
