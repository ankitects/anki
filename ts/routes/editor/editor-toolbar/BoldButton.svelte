<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { removeStyleProperties } from "@tslib/styling";

    import Icon from "$lib/components/Icon.svelte";
    import { boldIcon } from "$lib/components/icons";
    import type { MatchType } from "$lib/domlib/surround";

    import TextAttributeButton from "./TextAttributeButton.svelte";

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "B" || element.tagName === "STRONG") {
            return match.remove();
        }

        const fontWeight = element.style.fontWeight;
        if (fontWeight === "bold" || Number(fontWeight) >= 700) {
            return match.clear((): void => {
                if (
                    removeStyleProperties(element, "font-weight") &&
                    element.className.length === 0
                ) {
                    match.remove();
                }
            });
        }
    }
</script>

<TextAttributeButton
    tagName="b"
    {matcher}
    key="bold"
    tooltip={tr.editingBoldText()}
    keyCombination="Control+B"
>
    <Icon icon={boldIcon} />
</TextAttributeButton>
