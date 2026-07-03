<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { removeStyleProperties } from "@tslib/styling";

    import Icon from "$lib/components/Icon.svelte";
    import { italicIcon } from "$lib/components/icons";
    import type { MatchType } from "$lib/domlib/surround";

    import TextAttributeButton from "./TextAttributeButton.svelte";

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "I" || element.tagName === "EM") {
            return match.remove();
        }

        if (["italic", "oblique"].includes(element.style.fontStyle)) {
            return match.clear((): void => {
                if (
                    removeStyleProperties(element, "font-style") &&
                    element.className.length === 0
                ) {
                    return match.remove();
                }
            });
        }
    }
</script>

<TextAttributeButton
    tagName="i"
    {matcher}
    key="italic"
    tooltip={tr.editingItalicText()}
    keyCombination="Control+I"
>
    <Icon icon={italicIcon} />
</TextAttributeButton>
