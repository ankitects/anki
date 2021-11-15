<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Item from "../components/Item.svelte";
    export let id: string | undefined = undefined;
</script>

<Item {id}>
    <div class="row">
        <slot />
    </div>
</Item>
