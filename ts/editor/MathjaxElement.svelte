<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts" context="module">
    import { Mathjax } from "../editable/mathjax-element";
    import { decoratedElements } from "./DecoratedElements.svelte";

    decoratedElements.push(Mathjax);

    import { parsingInstructions } from "./plain-text-input";

    parsingInstructions.push("<style>anki-mathjax { white-space: pre; }</style>");
</script>
