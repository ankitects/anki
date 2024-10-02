<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Icon from "$lib/components/Icon.svelte";
    import { underlineIcon } from "$lib/components/icons";
    import type { MatchType } from "$lib/domlib/surround";

    import TextAttributeButton from "./TextAttributeButton.svelte";

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "U") {
            return match.remove();
        }
    }
</script>

<TextAttributeButton
    tagName="u"
    {matcher}
    key="underline"
    tooltip={tr.editingUnderlineText()}
    keyCombination="Control+U"
>
    <Icon icon={underlineIcon} />
</TextAttributeButton>
