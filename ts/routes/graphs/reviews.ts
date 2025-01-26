// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { GraphsResponse } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { dayLabel, timeSpan } from "@tslib/time";
import type { Bin, ScaleSequential } from "d3";
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
    interpolatePurples,
    interpolateReds,
    max,
    min,
    pointer,
    scaleLinear,
    scaleSequential,
    select,
    sum,
} from "d3";

import type { GraphBounds, TableDatum } from "./graph-helpers";
import { GraphRange, numericMap, setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

interface Reviews {
    learn: number;
    relearn: number;
    young: number;
    mature: number;
    filtered: number;
}

export interface GraphData {
    // indexed by day, where day is relative to today
    reviewCount: Map<number, Reviews>;
    reviewTime: Map<number, Reviews>;
}

type BinType = Bin<Map<number, Reviews[]>, number>;

export function gatherData(data: GraphsResponse): GraphData {
    return { reviewCount: numericMap(data.reviews!.count), reviewTime: numericMap(data.reviews!.time) };
}

enum BinIndex {
    Mature = 0,
    Young = 1,
    Relearn = 2,
    Learn = 3,
    Filtered = 4,
}

function totalsForBin(bin: BinType): number[] {
    const total = [0, 0, 0, 0, 0];
    for (const entry of bin) {
        total[BinIndex.Mature] += entry[1].mature;
        total[BinIndex.Young] += entry[1].young;
        total[BinIndex.Relearn] += entry[1].relearn;
        total[BinIndex.Learn] += entry[1].learn;
        total[BinIndex.Filtered] += entry[1].filtered;
    }

    return total;
}

/** eg idx=0 is mature count, idx=1 is mature+young count, etc */
function cumulativeBinValue(bin: BinType, idx: number): number {
    return sum(totalsForBin(bin).slice(0, idx + 1));
}

export function renderReviews(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData,
    range: GraphRange,
    showTime: boolean,
): TableDatum[] {
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const xMax = 1;
    let xMin = 0;
    // cap max to selected range
    switch (range) {
        case GraphRange.Month:
            xMin = -30;
            break;
        case GraphRange.ThreeMonths:
            xMin = -89;
            break;
        case GraphRange.Year:
            xMin = -364;
            break;
        case GraphRange.AllTime:
            xMin = min(sourceData.reviewCount.keys())!;
            break;
    }
    const desiredBars = Math.min(70, Math.abs(xMin!));

    const x = scaleLinear().domain([xMin!, xMax]);
    if (range === GraphRange.AllTime) {
        x.nice(desiredBars);
    }

    const sourceMap = showTime ? sourceData.reviewTime : sourceData.reviewCount;
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
        if (showTime) {
            return timeSpan(n / 1000, true);
        } else {
            if (Math.round(n) != n) {
                return "";
            } else {
                return localizedNumber(n);
            }
        }
    };

    const yMax = max(bins, (b: Bin<any, any>) => cumulativeBinValue(b, 4))!;
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

    const cappedRange = scaleLinear().range([0.3, 0.5]);
    const shiftedRange = scaleLinear().range([0.4, 0.7]);
    const darkerGreens = scaleSequential((n) => interpolateGreens(shiftedRange(n)!)).domain(x.domain() as any);
    const lighterGreens = scaleSequential((n) => interpolateGreens(cappedRange(n)!)).domain(x.domain() as any);
    const reds = scaleSequential((n) => interpolateReds(cappedRange(n)!)).domain(
        x.domain() as any,
    );
    const blues = scaleSequential((n) => interpolateBlues(cappedRange(n)!)).domain(
        x.domain() as any,
    );
    const purples = scaleSequential((n) => interpolatePurples(cappedRange(n)!)).domain(
        x.domain() as any,
    );

    function binColor(idx: BinIndex): ScaleSequential<string> {
        switch (idx) {
            case BinIndex.Mature:
                return darkerGreens;
            case BinIndex.Young:
                return lighterGreens;
            case BinIndex.Learn:
                return blues;
            case BinIndex.Relearn:
                return reds;
            case BinIndex.Filtered:
                return purples;
        }
    }

    function valueLabel(n: number): string {
        if (showTime) {
            return timeSpan(n / 1000);
        } else {
            return tr.statisticsReviews({ reviews: n });
        }
    }

    function tooltipText(d: BinType, cumulative: number): string {
        const day = dayLabel(d.x0!, d.x1!);
        const totals = totalsForBin(d);
        const dayTotal = valueLabel(sum(totals));
        let buf = `<table><tr><td>${day}</td><td align=end>${dayTotal}</td></tr>`;
        const lines: [BinIndex | null, string][] = [
            [BinIndex.Filtered, tr.statisticsCountsFilteredCards()],
            [BinIndex.Learn, tr.statisticsCountsLearningCards()],
            [BinIndex.Relearn, tr.statisticsCountsRelearningCards()],
            [BinIndex.Young, tr.statisticsCountsYoungCards()],
            [BinIndex.Mature, tr.statisticsCountsMatureCards()],
            [null, tr.statisticsRunningTotal()],
        ];
        for (const [idx, label] of lines) {
            let color: string;
            let detail: string;
            if (idx == null) {
                color = "transparent";
                detail = valueLabel(cumulative);
            } else {
                color = binColor(idx)(1);
                detail = valueLabel(totals[idx]);
            }
            buf += `<tr>
            <td><span style="color: ${color};">■</span> ${label}</td>
            <td align=end>${detail}</td>
            </tr>`;
        }
        return buf;
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

    for (const barNum of [0, 1, 2, 3, 4]) {
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

    // cumulative area

    const areaCounts = bins.map((d: any) => cumulativeBinValue(d, 4));
    areaCounts.unshift(0);
    const areaData = cumsum(areaCounts);
    const yCumMax = areaData.slice(-1)[0];
    const yAreaScale = y.copy().domain([0, yCumMax]).nice();

    if (yCumMax) {
        svg.select<SVGGElement>(".y2-ticks")
            .call((selection) =>
                selection.transition(trans).call(
                    axisRight(yAreaScale)
                        .ticks(bounds.height / 50)
                        .tickFormat(yTickFormat as any)
                        .tickSizeOuter(0),
                )
            )
            .attr("direction", "ltr");

        svg.select("path.cumulative-overlay")
            .datum(areaData)
            .attr(
                "d",
                area()
                    .curve(curveBasis)
                    .x((_d: [number, number], idx: number) => {
                        if (idx === 0) {
                            return x(bins[0].x0!)!;
                        } else {
                            return x(bins[idx - 1].x1!)!;
                        }
                    })
                    .y0(bounds.height - bounds.marginBottom)
                    .y1((d: any) => yAreaScale(d)!) as any,
            );
    }

    const hoverData: [Bin<number, number>, number][] = bins.map(
        (bin: Bin<number, number>, index: number) => [bin, areaData[index + 1]],
    );

    // hover/tooltip
    svg.select("g.hover-columns")
        .selectAll("rect")
        .data(hoverData)
        .join("rect")
        .attr("x", ([bin]) => x(bin.x0!))
        .attr("y", () => y(yMax))
        .attr("width", ([bin]) => barWidth(bin))
        .attr("height", () => y(0) - y(yMax))
        .on("mousemove", (event: MouseEvent, [bin, area]): void => {
            const [x, y] = pointer(event, document.body);
            showTooltip(tooltipText(bin as any, area), x, y);
        })
        .on("mouseout", hideTooltip);

    const periodDays = -xMin + 1;
    const studiedDays = sum(bins, (bin) => bin.length);
    const studiedPercent = (studiedDays / periodDays) * 100;
    const total = yCumMax;
    const periodAvg = total / periodDays;
    const studiedAvg = total / studiedDays;

    let totalString: string,
        averageForDaysStudied: string,
        averageForPeriod: string,
        averageAnswerTime: string,
        averageAnswerTimeLabel: string;
    if (showTime) {
        totalString = timeSpan(total / 1000, false);
        averageForDaysStudied = tr.statisticsMinutesPerDay({
            count: Math.round(studiedAvg / 1000 / 60),
        });
        averageForPeriod = tr.statisticsMinutesPerDay({
            count: Math.round(periodAvg / 1000 / 60),
        });
        averageAnswerTimeLabel = tr.statisticsAverageAnswerTimeLabel();

        // need to get total review count to calculate average time
        const countBins = bin()
            .value((m) => {
                return m[0];
            })
            .domain(x.domain() as any)(sourceData.reviewCount.entries() as any);
        const totalReviews = sum(countBins, (bin) => cumulativeBinValue(bin as any, 4));
        const totalSecs = total / 1000;
        const avgSecs = totalSecs / totalReviews;
        const cardsPerMin = (totalReviews * 60) / totalSecs;
        averageAnswerTime = tr.statisticsAverageAnswerTime({
            averageSeconds: avgSecs,
            cardsPerMinute: cardsPerMin,
        });
    } else {
        totalString = tr.statisticsReviews({ reviews: total });
        averageForDaysStudied = tr.statisticsReviewsPerDay({
            count: Math.round(studiedAvg),
        });
        averageForPeriod = tr.statisticsReviewsPerDay({
            count: Math.round(periodAvg),
        });
        averageAnswerTime = averageAnswerTimeLabel = "";
    }

    const tableData: TableDatum[] = [
        {
            label: tr.statisticsDaysStudied(),
            value: tr.statisticsAmountOfTotalWithPercentage({
                amount: studiedDays,
                total: periodDays,
                percent: (() => {
                    if (studiedPercent < 99.5) {
                        return localizedNumber(studiedPercent);
                    } else if (studiedPercent < 99.95) {
                        return localizedNumber(studiedPercent, 1);
                    } else if (studiedPercent < 100) {
                        return localizedNumber(studiedPercent, 2);
                    } else {
                        return "100";
                    }
                })(),
            }),
        },

        { label: tr.statisticsTotal(), value: totalString },

        {
            label: tr.statisticsAverageForDaysStudied(),
            value: averageForDaysStudied,
        },

        {
            label: tr.statisticsAverageOverPeriod(),
            value: averageForPeriod,
        },
    ];

    if (averageAnswerTime) {
        tableData.push({ label: averageAnswerTimeLabel, value: averageAnswerTime });
    }

    return tableData;
}
