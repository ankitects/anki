<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import Carousel from "bootstrap/js/dist/carousel";
    import Modal from "bootstrap/js/dist/modal";
    import { createEventDispatcher, getContext, onMount } from "svelte";

    import Badge from "../components/Badge.svelte";
    import Col from "../components/Col.svelte";
    import { modalsKey } from "../components/context-keys";
    import Row from "../components/Row.svelte";
    import { pageTheme } from "../sveltelib/theme";
    import HelpSection from "./HelpSection.svelte";
    import { infoCircle, manualIcon } from "./icons";
    import type { DeckOption } from "./types";

    export let title: string;
    export let url: string;
    export let startIndex = 0;
    export let helpSections: DeckOption[];

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

    onMount(() => {
        modal = new Modal(modalRef);
        carousel = new Carousel(carouselRef, { interval: false, ride: false });
        /* Bootstrap's Carousel.Event interface doesn't seem to work as a type here */
        carouselRef.addEventListener("slide.bs.carousel", (e: any) => {
            activeIndex = e.to;
        });
        dispatch("mount", { modal: modal, carousel: carousel });
        modals.set(modalKey, modal);
    });

    let activeIndex = startIndex;
</script>

<Badge on:click={() => modal.show()}>
    {@html infoCircle}
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
                <h1 class="modal-title" id="modalLabel">
                    {title}
                </h1>
                {#if url}
                    <a class="manual-badge" href={url}>
                        <Badge
                            iconSize={120}
                            tooltip={tr.helpOpenManualChapter({ name: title })}
                        >
                            {@html manualIcon}
                        </Badge>
                    </a>
                {/if}
                <button
                    type="button"
                    class="btn-close"
                    class:invert={$pageTheme.isDark}
                    data-bs-dismiss="modal"
                    aria-label="Close"
                />
            </div>
            <div class="modal-body">
                <Row --cols={4}>
                    <Col --col-size={1}>
                        <nav>
                            <div id="nav">
                                <ul>
                                    {#each helpSections as section, i}
                                        <li
                                            on:click={() => {
                                                activeIndex = i;
                                                carousel.to(activeIndex);
                                            }}
                                        >
                                            <span class:active={i == activeIndex}>
                                                {section.title}
                                            </span>
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
                                {#each helpSections as section, i}
                                    <div
                                        class="carousel-item"
                                        class:active={i == startIndex}
                                    >
                                        <HelpSection {section} />
                                    </div>
                                {/each}
                            </div>
                        </div>
                    </Col>
                </Row>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-primary" on:click={onOkClicked}
                    >OK</button
                >
            </div>
        </div>
    </div>
</div>

<style lang="scss">
    #nav {
        margin-bottom: 1.5rem;
    }

    .modal-title {
        margin-right: 0.75rem;
    }

    .manual-badge {
        text-decoration: none;
        color: var(--fg-subtle);
        &:hover {
            color: var(--fg);
        }
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

    li span {
        display: block;
        padding: 0.5rem 0.75rem;
        text-decoration: none;
        cursor: pointer;
        &:hover {
            background-color: var(--canvas-inset);
        }
        &.active {
            border-left: 4px solid var(--border-focus);
        }
    }
</style>
