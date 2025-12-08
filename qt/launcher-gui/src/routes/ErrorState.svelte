<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    import Icon from "$lib/components/Icon.svelte";
    import IconConstrain from "$lib/components/IconConstrain.svelte";
    import { alertIcon } from "$lib/components/icons";

    let {
        title,
        detail,
        footer = $bindable(),
    }: { title: string; detail: string; footer: any } = $props();

    footer = null;
</script>

<div class="outer">
    <div class="inner">
        <IconConstrain iconSize={300}>
            <Icon icon={alertIcon} />
        </IconConstrain>
    </div>
    <div class="title">{title}</div>
    <div class="detail">{detail}</div>
</div>

<style lang="scss">
    .outer {
        margin-top: 50px;
        margin-bottom: 10px;
    }

    .inner {
        width: 50px;
        height: 50px;
        margin: 0 auto;

        & :global(svg) {
            position: relative;
        }
    }

    .title {
        font-size: x-large;
        text-align: center;
    }

    .detail {
        font-size: medium;
        text-align: center;
    }
</style>
