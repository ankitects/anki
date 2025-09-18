<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { renderMarkdown } from "@tslib/helpers";
    import Carousel from "bootstrap/js/dist/carousel";
    import Modal from "bootstrap/js/dist/modal";
    import { createEventDispatcher, getContext, onDestroy, onMount } from "svelte";

    import { infoCircle } from "$lib/components/icons";
    import { registerModalClosingHandler } from "$lib/sveltelib/modal-closing";
    import { pageTheme } from "$lib/sveltelib/theme";

    import Badge from "./Badge.svelte";
    import Col from "./Col.svelte";
    import { modalsKey } from "./context-keys";
    import HelpSection from "./HelpSection.svelte";
    import Icon from "./Icon.svelte";
    import Row from "./Row.svelte";
    import { type HelpItem, HelpItemScheduler } from "./types";

    export let title: string;
    export let url: string;
    export let linkLabel: string | undefined = undefined;
    export let startIndex = 0;
    export let helpSections: HelpItem[];
    export let fsrs = false;

    export const modalKey: string = Math.random().toString(36).substring(2);

    const modals = getContext<Map<string, Modal>>(modalsKey);

    let modal: Modal;
    let carousel: Carousel;

    let modalRef: HTMLDivElement;
    let carouselRef: HTMLDivElement;

    function onOkClicked(): void {
        modal.hide();
    }

    const dispatch = createEventDispatcher();

    const { set: setModalOpen, remove: removeModalClosingHandler } =
        registerModalClosingHandler(onOkClicked);

    function onShown() {
        setModalOpen(true);
    }

    function onHidden() {
        setModalOpen(false);
    }

    onMount(() => {
        modalRef.addEventListener("shown.bs.modal", onShown);
        modalRef.addEventListener("hidden.bs.modal", onHidden);
        modal = new Modal(modalRef, { keyboard: false });
        carousel = new Carousel(carouselRef, { interval: false, ride: false });
        /* Bootstrap's Carousel.Event interface doesn't seem to work as a type here */
        carouselRef.addEventListener("slide.bs.carousel", (e: any) => {
            activeIndex = e.to;
        });
        dispatch("mount", { modal: modal, carousel: carousel });
        modals.set(modalKey, modal);
    });

    onDestroy(() => {
        removeModalClosingHandler();
        modalRef.removeEventListener("shown.bs.modal", onShown);
        modalRef.removeEventListener("hidden.bs.modal", onHidden);
    });

    let activeIndex = startIndex;
</script>

<Badge on:click={() => modal.show()} iconSize={125}>
    <Icon icon={infoCircle} />
</Badge>

<div
    bind:this={modalRef}
    class="modal fade"
    tabindex="-1"
    aria-labelledby="modalLabel"
    aria-hidden="true"
>
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
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
                                link: `<a href="${url}" title="${tr.helpOpenManualChapter({ name: linkLabel ?? title })}">${linkLabel ?? title}</a>`,
                            }),
                        )}
                    </div>
                {/if}
            </div>
            <div class="modal-body">
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
                                                    ? item.sched ===
                                                      HelpItemScheduler.SM2
                                                    : item.sched ==
                                                      HelpItemScheduler.FSRS}
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
            <div class="modal-footer">
                <button type="button" class="btn btn-primary" on:click={onOkClicked}>
                    {tr.helpOk()}
                </button>
            </div>
        </div>
    </div>
</div>

<style lang="scss">
    #nav {
        margin-bottom: 1.5rem;
    }

    .modal {
        z-index: 1066;
        background-color: rgba($color: black, $alpha: 0.5);
    }

    .modal-title {
        margin-inline-end: 0.75rem;
    }

    .modal-content {
        background-color: var(--canvas);
        color: var(--fg);
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
