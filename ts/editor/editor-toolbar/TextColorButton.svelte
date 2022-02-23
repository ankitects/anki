<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import type {
        FormattingNode,
        MatchType,
        SurroundFormat,
    } from "../../domlib/surround";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { withFontColor } from "../helpers";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import { removeEmptyStyle, Surrounder } from "../surround";
    import ColorPicker from "./ColorPicker.svelte";
    import type { RemoveFormat } from "./EditorToolbar.svelte";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { arrowIcon, textColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    export let color: string;

    $: transformedColor = transformColor(color);

    /**
     * The DOM will transform colors such as "#ff0000" to "rgb(255, 0, 0)".
     */
    function transformColor(color: string): string {
        const span = document.createElement("span");
        span.style.setProperty("color", color);
        return span.style.getPropertyValue("color");
    }

    function matcher(
        element: HTMLElement | SVGElement,
        match: MatchType<string>,
    ): void {
        if (
            withFontColor(element, (color: string): void => {
                if (color) {
                    match.setCache(color);
                    match.remove();
                }
            })
        ) {
            return;
        }

        const value = element.style.getPropertyValue("color");

        if (value.length === 0) {
            return;
        }

        match.setCache(value);
        match.clear((): void => {
            element.style.removeProperty("color");

            if (removeEmptyStyle(element) && element.className.length === 0) {
                match.remove();
            }
        });
    }

    function merger(
        before: FormattingNode<string>,
        after: FormattingNode<string>,
    ): boolean {
        return before.getCache(transformedColor) === after.getCache(transformedColor);
    }

    function formatter(node: FormattingNode<string>): boolean {
        const extension = node.extensions.find(
            (element: HTMLElement | SVGElement): boolean => element.tagName === "SPAN",
        );
        const color = node.getCache(transformedColor);

        if (extension) {
            extension.style.setProperty("color", color);
            return false;
        }

        const span = document.createElement("span");
        span.style.setProperty("color", color);
        node.range.toDOMRange().surroundContents(span);
        return true;
    }

    const format: SurroundFormat<string> = {
        matcher,
        merger,
        formatter,
    };

    const namedFormat: RemoveFormat<string> = {
        name: tr.editingTextColor(),
        show: true,
        active: true,
        format,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    const { focusedInput } = noteEditorContext.get();
    const surrounder = Surrounder.make();
    let disabled: boolean;

    $: if (editingInputIsRichText($focusedInput)) {
        surrounder.richText = $focusedInput;
        disabled = false;
    } else {
        surrounder.disable();
        disabled = true;
    }

    function setTextColor(): void {
        surrounder.overwriteSurround(format);
    }

    const setCombination = "F7";
    const pickCombination = "F8";
</script>

<WithColorHelper {color} let:colorHelperIcon let:setColor>
    <IconButton
        tooltip="{tr.editingTextColor()} ({getPlatformString(setCombination)})"
        {disabled}
        on:click={setTextColor}
        --border-left-radius="5px"
    >
        {@html textColorIcon}
        {@html colorHelperIcon}
    </IconButton>
    <Shortcut keyCombination={setCombination} on:action={setTextColor} />

    <IconButton
        tooltip="{tr.editingChangeColor()} ({getPlatformString(pickCombination)})"
        {disabled}
        widthMultiplier={0.5}
    >
        {@html arrowIcon}
        <ColorPicker
            keyCombination={pickCombination}
            on:input={(event) => {
                color = setColor(event);
                bridgeCommand(`lastTextColor:${color}`);
            }}
            on:change={() => setTextColor()}
        />
    </IconButton>
</WithColorHelper>
