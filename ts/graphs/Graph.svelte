<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import Badge from "../components/Badge.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import { fullscreenExitIcon, fullscreenIcon } from "./icons";

    export let title: string;
    export let subtitle: string | null = null;

    let fullscreen = false;

    function toggleFullscreen(): void {
        fullscreen = !fullscreen;
        document.body.classList.toggle("graphs-fullscreen", fullscreen);
    }
</script>

<div class="fullscreen-wrapper" class:fullscreen on:dblclick={toggleFullscreen}>
    <TitledContainer class="position-relative" {title}>
        <Badge
            tooltip={tr.actionsToggleFullscreen()}
            slot="badge"
            on:click={toggleFullscreen}
        >
            {@html fullscreen ? fullscreenExitIcon : fullscreenIcon}
        </Badge>
        <div class="graph d-flex flex-grow-1 flex-column justify-content-center">
            {#if subtitle}
                <div class="subtitle">{subtitle}</div>
            {/if}
            <slot />
        </div>
    </TitledContainer>
</div>

<style lang="scss">
    @use "sass/elevation" as *;

    .fullscreen {
        position: absolute;
        z-index: 100;
        top: 0;
        right: 0;
        bottom: 0;
        left: 0;
        display: flex;
        background: var(--canvas);
        border-radius: var(--border-radius-large);
    }

    .graph {
        max-height: 100%;
        page-break-inside: avoid;

        /* See graph-styles.ts for constants referencing global styles */
        :global(.graph-element-clickable) {
            cursor: pointer;
        }

        /* Customizing the standard x and y tick markers and text on the graphs.
         * The `tick` class is automatically added by d3. */
        :global(.tick) {
            :global(line) {
                opacity: 0.1;
            }

            :global(text) {
                opacity: 0.5;
                font-size: 10px;

                @media only screen and (max-width: 800px) {
                    font-size: 13px;
                }

                @media only screen and (max-width: 600px) {
                    font-size: 16px;
                }
            }
        }

        :global(.tick-odd) {
            @media only screen and (max-width: 600px) {
                /* on small screens, hide every second row on graphs that have
                 * marked the ticks as odd */
                display: none;
            }
        }

        &:focus {
            outline: 0;
        }
    }

    .subtitle {
        text-align: center;
        margin-bottom: 1em;
    }
</style>
