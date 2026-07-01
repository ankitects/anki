<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        Simulation,
        SimulationLinkDatum,
        SimulationNodeDatum,
        ZoomBehavior,
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
    interface TopicLabel {
        topic: string;
        x: number;
        y: number;
    }

    const width = 1200;
    const height = 900;

    let svgEl: SVGSVGElement;
    let transform: ZoomTransform = zoomIdentity;
    let zoomBehavior: ZoomBehavior<SVGSVGElement, unknown> | undefined;

    let simNodes: SimNode[] = [];
    let simLinks: SimLink[] = [];
    let simulation: Simulation<SimNode, SimLink> | undefined;
    let fitted = false;

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
        // so colours still change on non-FSRS or freshly studied decks.
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
        return text.length > 18 ? `${text.slice(0, 17)}…` : text;
    }

    // Grid of region centres so each topic is pulled into its own area.
    function computeRegions(topics: string[]): Map<string, { x: number; y: number }> {
        const map = new Map<string, { x: number; y: number }>();
        const n = topics.length;
        const cols = Math.max(1, Math.ceil(Math.sqrt(n)));
        const rows = Math.max(1, Math.ceil(n / cols));
        const cellW = width / cols;
        const cellH = height / rows;
        topics.forEach((topic, i) => {
            const row = Math.floor(i / cols);
            const col = i - row * cols;
            const itemsInRow = Math.min(cols, n - row * cols);
            const rowStart = (width - itemsInRow * cellW) / 2;
            map.set(topic, {
                x: rowStart + cellW * (col + 0.5),
                y: cellH * (row + 0.5),
            });
        });
        return map;
    }

    function rebuild(nextNodes: InputNode[], nextEdges: InputEdge[]): void {
        simulation?.stop();
        fitted = false;
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

        simulation = forceSimulation<SimNode, SimLink>(simNodes)
            .force(
                "link",
                forceLink<SimNode, SimLink>(simLinks)
                    .id((d) => d.id)
                    .distance(55)
                    .strength(0.15),
            )
            .force("charge", forceManyBody<SimNode>().strength(-170))
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
                forceCollide<SimNode>().radius((d) => d.r + 4),
            )
            .on("tick", () => {
                simNodes = [...simNodes];
                simLinks = [...simLinks];
            })
            .on("end", fitView);
    }

    $: rebuild(nodes, edges);

    // Show node (tag) labels when the graph is small enough not to get cluttered,
    // or for large clusters.
    $: showAllLabels = simNodes.length <= 30;

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

    // Place each topic label at the top of its own cluster (skip the "Other"
    // catch-all bucket, which isn't a real CFA topic).
    $: topicLabels = topicLabelsFrom(simNodes);

    function topicLabelsFrom(ns: SimNode[]): TopicLabel[] {
        const groups = new Map<string, { xSum: number; count: number; minY: number }>();
        for (const n of ns) {
            if (n.topic === "Other") {
                continue;
            }
            const g = groups.get(n.topic) ?? { xSum: 0, count: 0, minY: Infinity };
            g.xSum += n.x ?? 0;
            g.count += 1;
            g.minY = Math.min(g.minY, (n.y ?? 0) - n.r);
            groups.set(n.topic, g);
        }
        return [...groups].map(([topic, g]) => ({
            topic,
            x: g.xSum / g.count,
            y: g.minY - 12,
        }));
    }

    // Zoom/pan so the whole graph fits the viewport (once per dataset). Keeps
    // small decks from rendering as tiny nodes in a huge empty canvas.
    function fitView(): void {
        if (fitted || !svgEl || !zoomBehavior || simNodes.length === 0) {
            return;
        }
        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;
        for (const n of simNodes) {
            const x = n.x ?? 0;
            const y = n.y ?? 0;
            minX = Math.min(minX, x - n.r);
            minY = Math.min(minY, y - n.r - 18);
            maxX = Math.max(maxX, x + n.r);
            maxY = Math.max(maxY, y + n.r + 18);
        }
        if (!Number.isFinite(minX)) {
            return;
        }
        const bw = Math.max(maxX - minX, 1);
        const bh = Math.max(maxY - minY, 1);
        const scale = Math.max(
            0.2,
            Math.min(3, 0.9 * Math.min(width / bw, height / bh)),
        );
        const tx = width / 2 - scale * (minX + bw / 2);
        const ty = height / 2 - scale * (minY + bh / 2);
        zoomBehavior.transform(
            select(svgEl),
            zoomIdentity.translate(tx, ty).scale(scale),
        );
        fitted = true;
    }

    onMount(() => {
        zoomBehavior = zoom<SVGSVGElement, unknown>()
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
                    {#if node.r >= 16 || showAllLabels}
                        <text class="node-label" y={node.r + 12}>
                            {shorten(node.label)}
                        </text>
                    {/if}
                </g>
            {/each}
        </g>
        <!-- topic labels last so they always sit on top of the nodes -->
        <g class="topics">
            {#each topicLabels as label}
                <text class="topic-label" x={label.x} y={label.y}>{label.topic}</text>
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
        stroke: var(--canvas-inset);
        stroke-width: 3px;
        paint-order: stroke;
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
