<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import WithContext from "components/WithContext.svelte";
    import { fieldFocusedKey, inCodableKey } from "./context-keys";
</script>

<WithContext key={fieldFocusedKey} let:context={fieldFocused}>
    <WithContext key={inCodableKey} let:context={inCodable}>
        <slot disabled={!fieldFocused || inCodable} />
    </WithContext>
</WithContext>
