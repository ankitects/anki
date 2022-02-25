<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import portal from "../sveltelib/portal";
    export let target = document.body;
</script>

<div use:portal={target} hidden>
    <slot />
</div>
