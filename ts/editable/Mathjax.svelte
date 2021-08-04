<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { convertMathjax } from "./mathjax";

    export let mathjax: string;
    export let type: "inline" | "block" | "chemistry";

    $: delimiters = type === "inline" ? ["\\[", "\\]"] : ["\\(", "\\)"];
    $: converted = convertMathjax(`${delimiters[0]}${mathjax}${delimiters[1]}`);
</script>

{@html converted}
