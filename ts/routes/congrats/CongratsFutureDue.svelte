<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { afterUpdate } from "svelte";
    import {
        scaleLinear,
        scaleBand,
        axisBottom,
        axisLeft,
        select,
        max,
        interpolateOranges,
        scaleSequential,
    } from "d3";

    export let forecastData: Array<{
        total: number;
    }>;

    let svgElement: SVGElement;
    let chartData: Array<{ day: string; count: number }> = [];

    $: chartData = forecastData.slice(1, 8).map((day, idx) => ({
        day:
            ["Tomorrow", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7"][idx] ||
            `Day ${idx + 2}`,
        count: day.total,
    }));

    const margin = { top: 20, right: 20, bottom: 40, left: 40 };
    const width = 450 - margin.left - margin.right;
    const height = 160 - margin.top - margin.bottom;

    function drawChart() {
        if (!svgElement) {
            return;
        }

        select(svgElement).selectAll("*").remove();

        const svg = select(svgElement)
            .attr("width", width + margin.left + margin.right)
            .attr("height", height + margin.top + margin.bottom);

        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        const xScale = scaleBand()
            .domain(chartData.map((d) => d.day))
            .range([0, width])
            .padding(0.2);

        const maxCount = max(chartData, (d) => d.count) || 1;
        const yScale = scaleLinear().domain([0, maxCount]).nice().range([height, 0]);

        const colorScale = scaleSequential(interpolateOranges).domain([0, maxCount]);

        const xAxis = axisBottom(xScale);
        const yAxis = axisLeft(yScale)
            .ticks(Math.min(5, maxCount))
            .tickFormat((d) => (Number.isInteger(d) ? d.toString() : ""));

        g.append("g")
            .attr("class", "axis-x")
            .attr("transform", `translate(0,${height})`)
            .call(xAxis)
            .selectAll("text")
            .style("text-anchor", "middle")
            .style("font-size", "10px")
            .style("opacity", "0.7");

        g.append("g")
            .attr("class", "axis-y")
            .call(yAxis)
            .selectAll("text")
            .style("font-size", "10px")
            .style("opacity", "0.7");

        g.selectAll(".bar")
            .data(chartData)
            .enter()
            .append("rect")
            .attr("class", "bar")
            .attr("x", (d) => xScale(d.day)!)
            .attr("width", xScale.bandwidth())
            .attr("y", (d) => yScale(d.count))
            .attr("height", (d) => height - yScale(d.count))
            .attr("fill", (d) => (d.count > 0 ? colorScale(d.count) : "#f0f0f0"))
            .attr("stroke", "none")
            .style("shape-rendering", "crispEdges");

        g.selectAll(".label")
            .data(chartData.filter((d) => d.count > 0))
            .enter()
            .append("text")
            .attr("class", "label")
            .attr("x", (d) => xScale(d.day)! + xScale.bandwidth() / 2)
            .attr("y", (d) => yScale(d.count) - 5)
            .attr("text-anchor", "middle")
            .style("font-size", "10px")
            .style("font-weight", "bold")
            .style("opacity", "0.8")
            .style("fill", "#333")
            .text((d) => d.count);

        g.selectAll(".axis-y .tick line").style("opacity", "0.1").attr("x2", width);

        g.selectAll(".axis-x .tick line").style("opacity", "0.1");

        g.selectAll(".domain").style("opacity", "0.2");
    }

    $: if (chartData.length > 0) {
        setTimeout(drawChart, 10);
    }

    afterUpdate(() => {
        if (chartData.length > 0) {
            drawChart();
        }
    });

    const title = tr.statisticsFutureDueTitle();
</script>

<div class="future-due-container">
    <div class="graph-header">
        <h3 class="graph-title">{title}</h3>
    </div>

    <div class="chart-container">
        {#if chartData.some((d) => d.count > 0)}
            <svg bind:this={svgElement}></svg>
        {:else}
            <div class="no-data">No cards due in the next 7 days</div>
        {/if}
    </div>
</div>
