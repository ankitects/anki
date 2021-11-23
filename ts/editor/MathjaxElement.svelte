<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getDecoratedElements } from "./DecoratedElements.svelte";
    import { Mathjax } from "../editable/mathjax-element";

    const decoratedElements = getDecoratedElements();
    decoratedElements.push(Mathjax);

    import { parsingInstructions } from "./PlainTextInput.svelte";

    parsingInstructions.push("<style>anki-mathjax { white-space: pre; }</style>");
</script>
