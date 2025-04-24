<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import TitledContainer from "$lib/components/TitledContainer.svelte";

    // When title is null (default), the graph is inlined, not having TitledContainer wrapper.
    export let title: string | null = null;
    export let subtitle: string | null = null;
    export let onTitleClick: ((_e: MouseEvent | KeyboardEvent) => void) | null = null;
</script>

{#if title == null}
    <div class="graph d-flex flex-grow-1 flex-column justify-content-center">
        {#if subtitle}
            <div class="subtitle">{subtitle}</div>
        {/if}
        <slot />
    </div>
{:else}
    <TitledContainer class="d-flex flex-column" {title} {onTitleClick}>
        <slot slot="tooltip" name="tooltip"></slot>
        <div class="graph d-flex flex-grow-1 flex-column justify-content-center">
            {#if subtitle}
                <div class="subtitle">{subtitle}</div>
            {/if}
            <slot />
        </div>
    </TitledContainer>
{/if}

<style lang="scss">
    @use "$lib/sass/elevation" as *;
    .graph {
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
