<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { isDesktop } from "@tslib/platform";

    import ScrollArea from "./ScrollArea.svelte";

    let className: string = "";
    export { className as class };
</script>

<div class="page {className}">
    {#if $$slots.header}
        <div class="header">
            <slot name="header" />
        </div>
    {/if}
    <ScrollArea class="flex-grow-1" scrollY>
        <div class="page-content" class:mobile={!isDesktop()}>
            <slot />
        </div>
    </ScrollArea>
    {#if $$slots.footer}
        <div class="footer">
            <slot name="footer" />
        </div>
    {/if}
</div>

<style lang="scss">
    .page {
        width: 100vw;
        height: 100vh;
        display: flex;
        flex-direction: column;
        position: relative;

        .header,
        .footer {
            padding: 0.5rem;
        }

        .page-content {
            height: 100%;
            padding: 1rem;
            --gutter-block: 0.5rem;
            --gutter-inline: 0.5rem;
            @media only screen and (max-width: 1000px) {
                font-size: 14px;
            }
            @media only screen and (max-width: 600px) {
                padding: 0.25rem;
                --gutter-block: 0.25rem;
                --gutter-inline: 0;
                font-size: 13px;
            }
            &.mobile {
                margin-bottom: 50vh;
            }
        }
    }
</style>
