// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import { Stats } from "lib/proto";

import { timeSpan, dayLabel } from "lib/time";
import {
    interpolateGreens,
    interpolateReds,
    interpolateOranges,
    interpolatePurples,
    select,
    pointer,
    scaleLinear,
    scaleSequential,
    axisBottom,
    axisLeft,
    axisRight,
    area,
    curveBasis,
    min,
    histogram,
    sum,
    max,
    cumsum,
} from "d3";
import type { Bin } from "d3";

import type { TableDatum } from "./graph-helpers";
import { GraphBounds, setDataAvailable, GraphRange } from "./graph-helpers";
import { showTooltip, hideTooltip } from "./tooltip";
import * as tr from "lib/i18n";

interface Reviews {
    learn: number;
    relearn: number;
    young: number;
    mature: number;
    early: number;
}

export interface GraphData {
    // indexed by day, where day is relative to today
    reviewCount: Map<number, Reviews>;
    reviewTime: Map<number, Reviews>;
}

const ReviewKind = Stats.RevlogEntry.ReviewKind;
type BinType = Bin<Map<number, Reviews[]>, number>;

export function gatherData(data: Stats.GraphsResponse): GraphData {
    const reviewCount = new Map<number, Reviews>();
    const reviewTime = new Map<number, Reviews>();
    const empty = { mature: 0, young: 0, learn: 0, relearn: 0, early: 0 };

    for (const review of data.revlog as Stats.RevlogEntry[]) {
        if (review.reviewKind == ReviewKind.MANUAL) {
            // don't count days with only manual scheduling
            continue;
        }
        const day = Math.ceil(
            ((review.id as number) / 1000 - data.nextDayAtSecs) / 86400
        );
        const countEntry =
            reviewCount.get(day) ?? reviewCount.set(day, { ...empty }).get(day)!;
        const timeEntry =
            reviewTime.get(day) ?? reviewTime.set(day, { ...empty }).get(day)!;

        switch (review.reviewKind) {
            case ReviewKind.LEARNING:
                countEntry.learn += 1;
                timeEntry.learn += review.takenMillis;
                break;
            case ReviewKind.RELEARNING:
                countEntry.relearn += 1;
                timeEntry.relearn += review.takenMillis;
                break;
            case ReviewKind.REVIEW:
                if (review.lastInterval < 21) {
                    countEntry.young += 1;
                    timeEntry.young += review.takenMillis;
                } else {
                    countEntry.mature += 1;
                    timeEntry.mature += review.takenMillis;
                }
                break;
            case ReviewKind.EARLY_REVIEW:
                countEntry.early += 1;
                timeEntry.early += review.takenMillis;
                break;
        }
    }

    return { reviewCount, reviewTime };
}

function totalsForBin(bin: BinType): number[] {
    const total = [0, 0, 0, 0, 0];
    for (const entry of bin) {
        total[0] += entry[1].learn;
        total[1] += entry[1].relearn;
        total[2] += entry[1].young;
        total[3] += entry[1].mature;
        total[4] += entry[1].early;
    }

    return total;
}

/// eg idx=0 is mature count, idx=1 is mature+young count, etc
function cumulativeBinValue(bin: BinType, idx: number): number {
    return sum(totalsForBin(bin).slice(0, idx + 1));
}

export function renderReviews(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData,
    range: GraphRange,
    showTime: boolean
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

    const x = scaleLinear().domain([xMin!, xMax]).nice(desiredBars);
    x.domain([x.domain()[0], xMax]);

    const sourceMap = showTime ? sourceData.reviewTime : sourceData.reviewCount;
    const bins = histogram()
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
        .call((selection) =>
            selection.transition(trans).call(axisBottom(x).ticks(7).tickSizeOuter(0))
        )
        .attr("direction", "ltr");

    // y scale

    const yTickFormat = (n: number): string => {
        if (showTime) {
            return timeSpan(n / 1000, true);
        } else {
            if (Math.round(n) != n) {
                return "";
            } else {
                return n.toLocaleString();
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
                    .tickFormat(yTickFormat as any)
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
    const darkerGreens = scaleSequential((n) =>
        interpolateGreens(shiftedRange(n)!)
    ).domain(x.domain() as any);
    const lighterGreens = scaleSequential((n) =>
        interpolateGreens(cappedRange(n)!)
    ).domain(x.domain() as any);
    const reds = scaleSequential((n) => interpolateReds(cappedRange(n)!)).domain(
        x.domain() as any
    );
    const oranges = scaleSequential((n) => interpolateOranges(cappedRange(n)!)).domain(
        x.domain() as any
    );
    const purples = scaleSequential((n) => interpolatePurples(cappedRange(n)!)).domain(
        x.domain() as any
    );

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
        let buf = `<table><tr><td>${day}</td><td align=right>${dayTotal}</td></tr>`;
        const lines = [
            [oranges(1), tr.statisticsCountsLearningCards(), valueLabel(totals[0])],
            [reds(1), tr.statisticsCountsRelearningCards(), valueLabel(totals[1])],
            [lighterGreens(1), tr.statisticsCountsYoungCards(), valueLabel(totals[2])],
            [darkerGreens(1), tr.statisticsCountsMatureCards(), valueLabel(totals[3])],
            [purples(1), tr.statisticsCountsEarlyCards(), valueLabel(totals[4])],
            ["transparent", tr.statisticsRunningTotal(), valueLabel(cumulative)],
        ];
        for (const [colour, label, detail] of lines) {
            buf += `<tr>
            <td><span style="color: ${colour};">■</span> ${label}</td>
            <td align=right>${detail}</td>
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
            .attr("fill", (d: any) => {
                switch (idx) {
                    case 0:
                        return oranges(d.x0);
                    case 1:
                        return reds(d.x0);
                    case 2:
                        return lighterGreens(d.x0);
                    case 3:
                        return darkerGreens(d.x0);
                    case 4:
                        return purples(d.x0);
                }
            });
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
                (remove) =>
                    remove.call((remove) =>
                        remove.transition(trans).attr("height", 0).attr("y", y(0)!)
                    )
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
                        .tickSizeOuter(0)
                )
            )
            .attr("direction", "ltr");

        svg.select("path.cumulative-overlay")
            .datum(areaData as any)
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
                    .y1((d: any) => yAreaScale(d)!) as any
            );
    }

    const hoverData: [Bin<number, number>, number][] = bins.map(
        (bin: Bin<number, number>, index: number) => [bin, areaData[index + 1]]
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
        const countBins = histogram()
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
                percent: Math.round((studiedDays / periodDays) * 100),
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
