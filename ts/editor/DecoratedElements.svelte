<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import {
        CustomElementArray,
        DecoratedElementConstructor,
    } from "../editable/decorated";
    import { Mathjax } from "../editable/mathjax-element";

    const decoratedElements = new CustomElementArray<DecoratedElementConstructor>();
    decoratedElements.push(Mathjax);
</script>

<script lang="ts">
    import { setContext, decoratedElementsKey } from "./context";

    setContext(decoratedElementsKey, decoratedElements);
</script>

<slot />
