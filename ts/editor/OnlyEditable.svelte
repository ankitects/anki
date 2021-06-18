<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import WithContext from "components/WithContext.svelte";
    import { disabledKey } from "components/contextKeys";
    import { inCodableKey } from "./contextKeys";
</script>

<WithContext key={disabledKey} let:context={disabled}>
    <WithContext key={inCodableKey} let:context={inCodable}>
        <slot disabled={disabled || inCodable} />
    </WithContext>
</WithContext>
