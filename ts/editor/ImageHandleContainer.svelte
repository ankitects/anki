<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import ImageHandle from "./ImageHandle.svelte";

    export let hidden: boolean;
    export let top: number;
    export let left: number;
    export let width: number;
    export let height: number;
</script>

<div>
    <ImageHandle {hidden} {top} {left} {width} {height} />
</div>
