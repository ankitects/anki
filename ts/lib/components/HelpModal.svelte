<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { renderMarkdown } from "@tslib/helpers";
    import Carousel from "bootstrap/js/dist/carousel";
    import { createEventDispatcher, onMount } from "svelte";
    import Modal from "./Modal.svelte";

    import { infoCircle } from "$lib/components/icons";
    import { pageTheme } from "$lib/sveltelib/theme";

    import Badge from "./Badge.svelte";
    import Col from "./Col.svelte";
    import HelpSection from "./HelpSection.svelte";
    import Icon from "./Icon.svelte";
    import Row from "./Row.svelte";
    import { type HelpItem, HelpItemScheduler } from "./types";

    export let title: string;
    export let url: string;
    export let startIndex = 0;
    export let helpSections: HelpItem[];
    export let fsrs = false;

    let carousel: Carousel;

    let modal: Modal;
    let carouselRef: HTMLDivElement;

    const dispatch = createEventDispatcher();

    onMount(() => {
        carousel = new Carousel(carouselRef, { interval: false, ride: false });
        /* Bootstrap's Carousel.Event interface doesn't seem to work as a type here */
        carouselRef.addEventListener("slide.bs.carousel", (e: any) => {
            activeIndex = e.to;
        });
        dispatch("mount", { modal: modal, carousel: carousel });
    });

    let activeIndex = startIndex;
</script>

<Badge on:click={() => modal.show()} iconSize={125}>
    <Icon icon={infoCircle} />
</Badge>

<Modal bind:this={modal} dialogClass="modal-lg">
    <div slot="header" class="modal-header">
        <div style="display: flex;">
            <h1 class="modal-title" id="modalLabel">
                {title}
            </h1>
            <button
                type="button"
                class="btn-close"
                class:invert={$pageTheme.isDark}
                data-bs-dismiss="modal"
                aria-label="Close"
            ></button>
        </div>
        {#if url}
            <div class="chapter-redirect">
                {@html renderMarkdown(
                    tr.helpForMoreInfo({
                        link: `<a href="${url}" title="${tr.helpOpenManualChapter({
                            name: title,
                        })}">${title}</a>`,
                    }),
                )}
            </div>
        {/if}
    </div>
    <div slot="body" class="modal-body">
        <Row --cols={4}>
            <Col --col-size={1}>
                <nav>
                    <div id="nav">
                        <ul>
                            {#each helpSections as item, i}
                                <li>
                                    <button
                                        on:click={() => {
                                            activeIndex = i;
                                            carousel.to(activeIndex);
                                        }}
                                        class:active={i == activeIndex}
                                        class:d-none={fsrs
                                            ? item.sched === HelpItemScheduler.SM2
                                            : item.sched == HelpItemScheduler.FSRS}
                                    >
                                        {item.title}
                                    </button>
                                </li>
                            {/each}
                        </ul>
                    </div>
                </nav>
            </Col>
            <Col --col-size={3}>
                <div
                    id="helpSectionIndicators"
                    class="carousel slide"
                    bind:this={carouselRef}
                >
                    <div class="carousel-inner">
                        {#each helpSections as item, i}
                            <div
                                class="carousel-item"
                                class:active={i == startIndex}
                                class:d-none={fsrs
                                    ? item.sched === HelpItemScheduler.SM2
                                    : item.sched == HelpItemScheduler.FSRS}
                            >
                                <HelpSection {item} />
                            </div>
                        {/each}
                    </div>
                </div>
            </Col>
        </Row>
    </div>
    <div slot="footer" class="modal-footer">
        <button type="button" class="btn btn-primary" on:click={modal.onOkClicked}>
            {tr.helpOk()}
        </button>
    </div>
</Modal>

<style lang="scss">
    #nav {
        margin-bottom: 1.5rem;
    }

    .modal-title {
        margin-inline-end: 0.75rem;
    }

    :global(.modal-content) {
        border-radius: var(--border-radius-medium, 10px);
    }

    .invert {
        filter: invert(1) grayscale(100%) brightness(200%);
    }

    ul {
        list-style-type: none;
        margin: 0;
        padding: 0;
    }

    li button {
        display: block;
        padding: 0.5rem 0.75rem;
        text-decoration: none;
        text-align: start;
        min-width: 250px;
        background-color: var(--canvas);
        border: 1px solid transparent;
        cursor: pointer;
        border-radius: 0;
        &:hover {
            background-color: var(--canvas-inset);
        }
        &.active {
            border-inline-start: 4px solid var(--border-focus);
        }
    }

    .modal-header {
        flex-direction: column;
        align-items: normal;
        padding-bottom: 0;
    }

    .chapter-redirect {
        width: 100%;
        color: var(--fg-subtle);
        font-size: small;
    }
</style>
