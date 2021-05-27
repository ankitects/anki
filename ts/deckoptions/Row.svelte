<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let id: string | undefined = undefined;
</script>

<div {id} class="row gx-0 gy-2 mt-0">
    <slot />
</div>
