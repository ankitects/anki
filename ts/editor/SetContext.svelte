<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let setter: (value: any) => void;
    export let value: any;

    setter(value);
</script>

<slot />
