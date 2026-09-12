<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { removeStyleProperties } from "@tslib/styling";

    import Icon from "$lib/components/Icon.svelte";
    import { subscriptIcon } from "$lib/components/icons";
    import type { MatchType } from "$lib/domlib/surround";

    import TextAttributeButton from "./TextAttributeButton.svelte";

    export function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "SUB") {
            return match.remove();
        }

        if (element.style.verticalAlign === "sub") {
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
    tagName="sub"
    {matcher}
    key="subscript"
    tooltip={tr.editingSubscript()}
    keyCombination="Control+Shift+="
    exclusiveNames={["superscript"]}
>
    <Icon icon={subscriptIcon} />
</TextAttributeButton>
