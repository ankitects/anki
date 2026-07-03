<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { removeStyleProperties } from "@tslib/styling";

    import Icon from "$lib/components/Icon.svelte";
    import { superscriptIcon } from "$lib/components/icons";
    import type { MatchType } from "$lib/domlib/surround";

    import TextAttributeButton from "./TextAttributeButton.svelte";

    export function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "SUP") {
            return match.remove();
        }

        if (element.style.verticalAlign === "super") {
            return match.clear((): void => {
                if (
                    removeStyleProperties(element, "vertical-align") &&
                    element.className.length === 0
                ) {
                    return match.remove();
                }
            });
        }
    }
</script>

<TextAttributeButton
    tagName="sup"
    {matcher}
    key="superscript"
    tooltip={tr.editingSuperscript()}
    keyCombination="Control+="
    exclusiveNames={["subscript"]}
>
    <Icon icon={superscriptIcon} />
</TextAttributeButton>
