<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ColorPicker from "../../components/ColorPicker.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import type {
        ElementNode,
        FormattingNode,
        MatchType,
        SurroundFormat,
    } from "../../domlib/surround";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getBaseSurrounder, removeEmptyStyle } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { arrowIcon, highlightColorIcon } from "./icons";
    import WithColorHelper from "./WithColorHelper.svelte";

    export let color: string;

    $: transformedColor = transformColor(color);

    /**
     * The DOM will transform colors such as "#ff0000" to "rgb(256, 0, 0)".
     */
    function transformColor(color: string): string {
        const span = document.createElement("span");
        span.style.setProperty("background-color", color);
        return span.style.getPropertyValue("background-color");
    }

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        const value = element.style.getPropertyValue("background-color");

        if (value.length === 0) {
            return;
        }

        match.setCache(value);
        match.clear((): void => {
            element.style.removeProperty("background-color");

            if (removeEmptyStyle(element) && element.className.length === 0) {
                match.remove();
            }
        });
    }

    function merger(before: FormattingNode, after: FormattingNode): boolean {
        return before.getCache(transformedColor) === after.getCache(transformedColor);
    }

    function ascender(node: FormattingNode, elementNode: ElementNode): boolean {
        return (
            !elementNode.match.matches ||
            node.getCache(transformedColor) === elementNode.match.cache
        );
    }

    function formatter(node: FormattingNode): boolean {
        const span = document.createElement("span");
        span.style.setProperty("background-color", node.getCache(transformedColor));
        node.range.toDOMRange().surroundContents(span);
        return true;
    }

    const format: SurroundFormat = {
        matcher,
        merger,
        ascender,
        formatter,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.push(format);

    const { focusedInput } = noteEditorContext.get();
    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: surrounder = disabled ? null : getBaseSurrounder(input, format, [format]);

    function setTextColor(): void {
        surrounder?.surroundCommand();
    }
</script>

<WithColorHelper {color} let:colorHelperIcon let:setColor>
    <IconButton
        tooltip={tr.editingSetTextColor()}
        {disabled}
        on:click={setTextColor}
        --border-left-radius="5px"
    >
        {@html highlightColorIcon}
        {@html colorHelperIcon}
    </IconButton>

    <IconButton tooltip={tr.editingChangeColor()} {disabled} widthMultiplier={0.5}>
        {@html arrowIcon}
        <ColorPicker
            on:change={(event) => {
                color = setColor(event);
                bridgeCommand(`lastHighlightColor:${color}`);
                setTextColor();
            }}
        />
    </IconButton>
</WithColorHelper>
