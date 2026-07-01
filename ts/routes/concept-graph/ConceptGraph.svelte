<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        Simulation,
        SimulationLinkDatum,
        SimulationNodeDatum,
        ZoomTransform,
    } from "d3";
    import {
        drag,
        forceCollide,
        forceLink,
        forceManyBody,
        forceSimulation,
        forceX,
        forceY,
        select,
        zoom,
        zoomIdentity,
    } from "d3";
    import { onDestroy, onMount } from "svelte";

    import { topicOf } from "./topics";

    type Bucket = "mastered" | "learning" | "weak" | "new";

    interface InputNode {
        id: number;
        label: string;
        cardCount: number;
        withMemoryState: number;
        averageRetrievability: number;
        reviewedCount: number;
    }
    interface InputEdge {
        source: number;
        target: number;
        weight: number;
    }

    export let nodes: InputNode[];
    export let edges: InputEdge[];

    interface SimNode extends SimulationNodeDatum {
        id: number;
        label: string;
        cardCount: number;
        bucket: Bucket;
        topic: string;
        r: number;
    }
    interface SimLink extends SimulationLinkDatum<SimNode> {
        weight: number;
    }
    interface TopicRegion {
        topic: string;
        x: number;
        y: number;
        labelY: number;
    }

    const width = 1200;
    const height = 900;

    let svgEl: SVGSVGElement;
    let transform: ZoomTransform = zoomIdentity;

    let simNodes: SimNode[] = [];
    let simLinks: SimLink[] = [];
    let regions: TopicRegion[] = [];
    let simulation: Simulation<SimNode, SimLink> | undefined;

    function bucketOf(node: InputNode): Bucket {
        // Prefer FSRS retrievability when the cluster has memory state.
        if (node.withMemoryState > 0) {
            const r = node.averageRetrievability;
            if (r >= 0.9) {
                return "mastered";
            }
            if (r >= 0.7) {
                return "learning";
            }
            return "weak";
        }
        // No FSRS data: fall back to study progress (cards graduated to review),
        // so colours are still meaningful on non-FSRS or fresh decks.
        if (node.cardCount === 0 || node.reviewedCount === 0) {
            return "new";
        }
        return node.reviewedCount >= node.cardCount ? "mastered" : "learning";
    }

    function radiusOf(count: number): number {
        return Math.max(6, Math.min(34, 5 + Math.sqrt(count) * 3));
    }

    function readable(label: string): string {
        return label.replace(/_/g, " ");
    }

    function shorten(label: string): string {
        const text = readable(label);
        return text.length > 16 ? `${text.slice(0, 15)}…` : text;
    }

    // Lay the topics out on a centred grid so each subject gets its own region.
    // Nodes anchor to the lower part of the cell; the label sits above them.
    function computeRegions(topics: string[]): Map<string, TopicRegion> {
        const map = new Map<string, TopicRegion>();
        const n = topics.length;
        const cols = Math.max(1, Math.ceil(Math.sqrt(n)));
        const rows = Math.max(1, Math.ceil(n / cols));
        const cellW = width / cols;
        const cellH = height / rows;

        topics.forEach((topic, i) => {
            const row = Math.floor(i / cols);
            const col = i - row * cols;
            const itemsInRow = Math.min(cols, n - row * cols);
            // centre each row so leftover cells don't skew the layout
            const rowStart = (width - itemsInRow * cellW) / 2;
            map.set(topic, {
                topic,
                x: rowStart + cellW * (col + 0.5),
                y: cellH * row + cellH * 0.6,
                labelY: cellH * row + cellH * 0.16,
            });
        });
        return map;
    }

    function rebuild(nextNodes: InputNode[], nextEdges: InputEdge[]): void {
        simulation?.stop();
        simNodes = nextNodes.map((n) => ({
            id: n.id,
            label: n.label,
            cardCount: n.cardCount,
            bucket: bucketOf(n),
            topic: topicOf(n.label),
            r: radiusOf(n.cardCount),
        }));
        simLinks = nextEdges.map((e) => ({
            source: e.source,
            target: e.target,
            weight: e.weight,
        }));

        const topics = [...new Set(simNodes.map((n) => n.topic))].sort();
        const regionMap = computeRegions(topics);
        regions = topics.map((t) => regionMap.get(t)!);

        simulation = forceSimulation<SimNode, SimLink>(simNodes)
            // Weak links: co-occurrence still shows as edges, but topic anchoring
            // dominates so subjects don't get pulled into one clump.
            .force(
                "link",
                forceLink<SimNode, SimLink>(simLinks)
                    .id((d) => d.id)
                    .distance(40)
                    .strength(0.02),
            )
            .force("charge", forceManyBody<SimNode>().strength(-80))
            .force(
                "x",
                forceX<SimNode>((d) => regionMap.get(d.topic)!.x).strength(0.45),
            )
            .force(
                "y",
                forceY<SimNode>((d) => regionMap.get(d.topic)!.y).strength(0.45),
            )
            .force(
                "collide",
                forceCollide<SimNode>().radius((d) => d.r + 3),
            )
            .on("tick", () => {
                simNodes = [...simNodes];
                simLinks = [...simLinks];
            });
    }

    $: rebuild(nodes, edges);

    $: resolvedLinks = simLinks
        .filter((l) => typeof l.source === "object" && typeof l.target === "object")
        .map((l) => {
            const source = l.source as SimNode;
            const target = l.target as SimNode;
            return {
                x1: source.x ?? 0,
                y1: source.y ?? 0,
                x2: target.x ?? 0,
                y2: target.y ?? 0,
                weight: l.weight,
            };
        });

    onMount(() => {
        const zoomBehavior = zoom<SVGSVGElement, unknown>()
            .scaleExtent([0.2, 8])
            .filter((event) => {
                if (event.type === "wheel") {
                    return true;
                }
                const target = event.target as Element | null;
                if (target?.closest(".node")) {
                    return false;
                }
                return !(event as MouseEvent).button;
            })
            .on("zoom", (event) => {
                transform = event.transform;
            });
        select(svgEl).call(zoomBehavior);
    });

    // Svelte action: make a node draggable. Coordinates are read in the node
    // group's parent space, which sits inside the zoom transform, so dragging
    // stays correct at any zoom level.
    function dragNode(el: SVGGElement, node: SimNode) {
        let current = node;
        const behavior = drag<SVGGElement, unknown>()
            .on("start", (event) => {
                if (!event.active) {
                    simulation?.alphaTarget(0.3).restart();
                }
                current.fx = current.x;
                current.fy = current.y;
            })
            .on("drag", (event) => {
                current.fx = event.x;
                current.fy = event.y;
            })
            .on("end", (event) => {
                if (!event.active) {
                    simulation?.alphaTarget(0);
                }
                current.fx = null;
                current.fy = null;
            });
        select(el).call(behavior);
        return {
            update(next: SimNode) {
                current = next;
            },
            destroy() {
                select(el).on(".drag", null);
            },
        };
    }

    onDestroy(() => simulation?.stop());
</script>

<svg
    bind:this={svgEl}
    class="concept-graph"
    viewBox="0 0 {width} {height}"
    preserveAspectRatio="xMidYMid meet"
>
    <rect class="pan-surface" x="0" y="0" {width} {height} />
    <g transform={transform.toString()}>
        <g class="links">
            {#each resolvedLinks as link}
                <line
                    x1={link.x1}
                    y1={link.y1}
                    x2={link.x2}
                    y2={link.y2}
                    stroke-width={Math.min(1 + link.weight, 4)}
                />
            {/each}
        </g>
        <g class="nodes">
            {#each simNodes as node (node.id)}
                <g
                    class="node node--{node.bucket}"
                    transform="translate({node.x ?? 0},{node.y ?? 0})"
                    use:dragNode={node}
                >
                    <circle r={node.r}>
                        <title>{readable(node.label)} · {node.cardCount}</title>
                    </circle>
                    {#if node.r >= 16}
                        <text class="node-label" dy="0.32em">
                            {shorten(node.label)}
                        </text>
                    {/if}
                </g>
            {/each}
        </g>
        <!-- topic labels last so they always sit on top of the nodes -->
        <g class="topics">
            {#each regions as region}
                <text class="topic-label" x={region.x} y={region.labelY}>
                    {region.topic}
                </text>
            {/each}
        </g>
    </g>
</svg>

<style lang="scss">
    .concept-graph {
        width: 100%;
        height: auto;
        display: block;
        background: var(--canvas-inset);
        border: 1px solid var(--border-subtle);
        border-radius: var(--border-radius-medium);
        cursor: grab;

        &:active {
            cursor: grabbing;
        }
    }

    .pan-surface {
        fill: none;
        pointer-events: all;
    }

    .topic-label {
        fill: var(--fg);
        // background-coloured halo so the name stays legible over any node
        stroke: var(--canvas-inset);
        stroke-width: 4px;
        paint-order: stroke;
        font-size: 15px;
        font-weight: 700;
        text-anchor: middle;
        pointer-events: none;
    }

    .links line {
        stroke: var(--border);
        opacity: 0.6;
    }

    .node {
        cursor: move;
    }

    .node circle {
        stroke: var(--canvas);
        stroke-width: 1.5;
    }

    .node-label {
        fill: var(--fg-subtle);
        font-size: 10px;
        text-anchor: middle;
        pointer-events: none;
    }

    .node--mastered circle {
        fill: var(--state-review);
    }
    .node--learning circle {
        fill: var(--state-buried);
    }
    .node--weak circle {
        fill: var(--state-learn);
    }
    .node--new circle {
        fill: var(--state-new);
    }
</style>
