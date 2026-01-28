<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Icon from "$lib/components/Icon.svelte";
    import { strikethroughIcon } from "$lib/components/icons";
    import type { MatchType } from "$lib/domlib/surround";

    import TextAttributeButton from "./TextAttributeButton.svelte";

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "S") {
            return match.remove();
        }
    }
</script>

<TextAttributeButton
    tagName="s"
    {matcher}
    key="strikethrough"
    tooltip={tr.editingStrikethroughText()}
    keyCombination="Control+Shift+S"
>
    <Icon icon={strikethroughIcon} />
</TextAttributeButton>
