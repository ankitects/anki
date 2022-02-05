<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { Mathjax } from "../editable/mathjax-element";
    import { context } from "./DecoratedElements.svelte";

    const decoratedElements = context.get();
    decoratedElements.push(Mathjax);

    import { parsingInstructions } from "./plain-text-input";

    parsingInstructions.push("<style>anki-mathjax { white-space: pre; }</style>");
</script>
