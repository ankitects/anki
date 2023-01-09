<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@tslib/ftl";
    import { removeStyleProperties } from "@tslib/styling";
    import { singleCallback } from "@tslib/typing";
    import { onMount } from "svelte";

    import IconButton from "../../components/IconButton.svelte";
    import type { FormattingNode, MatchType } from "../../domlib/surround";
    import { chevronDown } from "../icons";
    import { surrounder } from "../rich-text-input";
    import ColorPicker from "./ColorPicker.svelte";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { highlightColorIcon } from "./icons";
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

    function matcher(
        element: HTMLElement | SVGElement,
        match: MatchType<string>,
    ): void {
        const value = element.style.getPropertyValue("background-color");

        if (value.length === 0) {
            return;
        }

        match.setCache(value);
        match.clear((): void => {
            if (
                removeStyleProperties(element, "background-color") &&
                element.className.length === 0
            ) {
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
            extension.style.setProperty("background-color", color);
            return false;
        }

        const span = document.createElement("span");
        span.style.setProperty("background-color", color);
        node.range.toDOMRange().surroundContents(span);
        return true;
    }

    const key = "highlightColor";

    const format = {
        matcher,
        merger,
        formatter,
    };

    const namedFormat = {
        key,
        name: tr.editingTextHighlightColor(),
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    function setTextColor(): void {
        surrounder.overwriteSurround(key);
    }

    let disabled: boolean;

    onMount(() =>
        singleCallback(
            surrounder.active.subscribe((value) => (disabled = !value)),
            surrounder.registerFormat(key, format),
        ),
    );
</script>

<WithColorHelper {color} let:colorHelperIcon let:setColor>
    <IconButton
        tooltip={tr.editingTextHighlightColor()}
        {disabled}
        on:click={setTextColor}
    >
        {@html highlightColorIcon}
        {@html colorHelperIcon}
    </IconButton>

    <IconButton
        tooltip={tr.editingChangeColor()}
        {disabled}
        widthMultiplier={0.5}
        iconSize={120}
        --border-right-radius="5px"
    >
        {@html chevronDown}
        <ColorPicker
            value={color}
            on:input={(event) => {
                color = setColor(event);
                bridgeCommand(`lastHighlightColor:${color}`);
            }}
            on:change={() => setTextColor()}
        />
    </IconButton>
</WithColorHelper>
