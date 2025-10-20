<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    let { footer = $bindable() }: { footer: any } = $props();

    footer = null;
</script>

<div class="outer">
    <div id="label">TODO: Uninstall</div>
</div>

<style lang="scss">
    .outer {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
    }

    #label {
        font-size: x-large;
        text-align: center;
    }
</style>
