<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    export let label: string;
    export let value: string;
    export let selected = false;
</script>

<option {selected} {value}>{label}</option>
