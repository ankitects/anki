<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let value: string | undefined = undefined;
    export let selected = false;
</script>

<option {value} {selected}>
    <slot />
</option>
