import type { SimulateFsrsReviewResponse } from "@generated/anki/scheduler_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { timeSpan } from "@tslib/time";
import {
    area,
    axisBottom,
    axisLeft,
    axisRight,
    bin,
    cumsum,
    curveBasis,
    interpolateBlues,
    interpolateGreens,
    interpolateOranges,
    interpolatePurples,
    interpolateReds,
    max,
    pointer,
    scaleLinear,
    scaleSequential,
    select,
    sum,
    type Bin,
    type ScaleSequential,
} from "d3";

import type { GraphBounds, TableDatum } from "./graph-helpers";
import { setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip";

interface Reviews {
    new: number;
    review: number;
    acquisition: number;
    cost: number;
}

function convertSimulateResponseToMap(data: SimulateFsrsReviewResponse): Map<number, Reviews> {
    const result = new Map<number, Reviews>();

    // 假设所有数组的长度相同
    const length = data.accumulatedKnowledgeAcquisition.length;

    for (let i = 0; i < length; i++) {
        result.set(i, {
            new: data.dailyNewCount[i],
            review: data.dailyReviewCount[i],
            acquisition: data.accumulatedKnowledgeAcquisition[i],
            cost: data.dailyTimeCost[i]
        });
    }

    return result;
}

enum BinIndex {
    New = 0,
    Review = 1,
}

type BinType = Bin<Map<number, Reviews[]>, number>;

function totalsForBin(bin: BinType): number[] {
    const total = [0, 0];
    for (const entry of bin) {
        total[BinIndex.New] += entry[1].new;
        total[BinIndex.Review] += entry[1].review;
    }

    return total;
}


function cumulativeBinValue(bin: BinType, idx: number): number {
    return sum(totalsForBin(bin).slice(0, idx + 1));
}

export function renderSimulationChart(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: SimulateFsrsReviewResponse,
): TableDatum[] {
    // Prepare data
    const sourceMap = convertSimulateResponseToMap(data);

    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const xMax = max(sourceMap.keys());
    const xMin = 0;
    const desiredBars = Math.min(70, Math.abs(xMax!));

    const x = scaleLinear().domain([xMin, xMax!]);
    x.nice(desiredBars);


    const bins = bin()
        .value((m) => {
            return m[0];
        })
        .domain(x.domain() as any)
        .thresholds(x.ticks(desiredBars))(sourceMap.entries() as any);

    // empty graph?
    const totalDays = sum(bins, (bin) => bin.length);
    if (!totalDays) {
        setDataAvailable(svg, false);
        return [];
    } else {
        setDataAvailable(svg, true);
    }


    x.range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks")
        .call((selection) => selection.transition(trans).call(axisBottom(x).ticks(7).tickSizeOuter(0)))
        .attr("direction", "ltr");

    // y scale
    
    const yTickFormat = (n: number): string => {
        if (Math.round(n) != n) {
            return "";
        } else {
            return localizedNumber(n);
        }
    };

    const yMax = max(bins, (b: Bin<any, any>) => cumulativeBinValue(b, 2))!;
    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax])
        .nice();
    svg.select<SVGGElement>(".y-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisLeft(y)
                    .ticks(bounds.height / 50)
                    .tickSizeOuter(0)
                    .tickFormat(yTickFormat as any),
            )
        )
        .attr("direction", "ltr");

    // x bars

    function barWidth(d: Bin<number, number>): number {
        const width = Math.max(0, x(d.x1!) - x(d.x0!) - 1);
        return width ?? 0;
    }

    const adjustedRange = scaleLinear().range([0.7, 0.3]);
    const cappedRange = scaleLinear().range([0.3, 0.5]);
    const blues = scaleSequential((n) => interpolateBlues(adjustedRange(n)!)).domain(x.domain() as any);
    const greens = scaleSequential((n) => interpolateGreens(cappedRange(n)!)).domain(x.domain() as any);

    function binColor(idx: BinIndex): ScaleSequential<string> {
        switch (idx) {
            case BinIndex.New:
                return blues;
            case BinIndex.Review:
                return greens;
        }
    }
    
    const updateBar = (sel: any, idx: number): any => {
        return sel
            .attr("width", barWidth)
            .transition(trans)
            .attr("x", (d: any) => x(d.x0))
            .attr("y", (d: any) => y(cumulativeBinValue(d, idx))!)
            .attr("height", (d: any) => y(0)! - y(cumulativeBinValue(d, idx))!)
            .attr("fill", (d: any) => binColor(idx)(d.x0));
    };

    for (const barNum of [0, 1]) {
    svg.select(`g.bars${barNum}`)
        .selectAll("rect")
        .data(bins)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("rx", 1)
                    .attr("x", (d: any) => x(d.x0)!)
                    .attr("y", y(0)!)
                    .attr("height", 0)
                    .call((d) => updateBar(d, barNum)),
            (update) => update.call((d) => updateBar(d, barNum)),
            (remove) => remove.call((remove) => remove.transition(trans).attr("height", 0).attr("y", y(0)!)),
        );
    }


    const tableData: TableDatum[] = [
    ];

    return tableData;
}
