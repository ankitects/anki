<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { CustomElementArray } from "../editable/decorated";

    const decoratedElements = new CustomElementArray();

    export { decoratedElements };
</script>

<slot />
