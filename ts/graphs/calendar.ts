// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "anki/backend_proto";
import {
    interpolateBlues,
    select,
    pointer,
    scaleLinear,
    scaleSequentialSqrt,
    timeDay,
    timeYear,
    timeSunday,
    timeMonday,
    timeFriday,
    timeSaturday,
} from "d3";
import type { CountableTimeInterval } from "d3";
import { showTooltip, hideTooltip } from "./tooltip";
import {
    GraphBounds,
    setDataAvailable,
    RevlogRange,
    SearchDispatch,
} from "./graph-helpers";
import type { I18n } from "anki/i18n";

export interface GraphData {
    // indexed by day, where day is relative to today
    reviewCount: Map<number, number>;
    timeFunction: CountableTimeInterval;
    weekdayLabels: number[];
}

interface DayDatum {
    day: number;
    count: number;
    // 0-51
    weekNumber: number;
    // 0-6
    weekDay: number;
    date: Date;
}

type WeekdayType = pb.BackendProto.GraphPreferences.Weekday;
const Weekday = pb.BackendProto.GraphPreferences.Weekday; /* enum */

export function gatherData(
    data: pb.BackendProto.GraphsOut,
    firstDayOfWeek: WeekdayType
): GraphData {
    const reviewCount = new Map<number, number>();

    for (const review of data.revlog as pb.BackendProto.RevlogEntry[]) {
        if (review.buttonChosen == 0) {
            continue;
        }
        const day = Math.ceil(
            ((review.id as number) / 1000 - data.nextDayAtSecs) / 86400
        );
        const count = reviewCount.get(day) ?? 0;
        reviewCount.set(day, count + 1);
    }

    const timeFunction =
        firstDayOfWeek === Weekday.MONDAY
            ? timeMonday
            : firstDayOfWeek === Weekday.FRIDAY
            ? timeFriday
            : firstDayOfWeek === Weekday.SATURDAY
            ? timeSaturday
            : timeSunday;

    const weekdayLabels: number[] = [];
    for (let i = 0; i < 7; i++) {
        weekdayLabels.push((firstDayOfWeek + i) % 7);
    }

    return { reviewCount, timeFunction, weekdayLabels };
}

export function renderCalendar(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData,
    dispatch: SearchDispatch,
    targetYear: number,
    i18n: I18n,
    nightMode: boolean,
    revlogRange: RevlogRange,
    setFirstDayOfWeek: (d: number) => void
): void {
    const svg = select(svgElem);
    const now = new Date();
    const nowForYear = new Date();
    nowForYear.setFullYear(targetYear);

    const x = scaleLinear()
        .range([bounds.marginLeft, bounds.width - bounds.marginRight])
        .domain([-1, 53]);

    // map of 0-365 -> day
    const dayMap: Map<number, DayDatum> = new Map();
    let maxCount = 0;
    for (const [day, count] of sourceData.reviewCount.entries()) {
        const date = new Date(now.getTime() + day * 86400 * 1000);
        if (date.getFullYear() != targetYear) {
            continue;
        }
        const weekNumber = sourceData.timeFunction.count(timeYear(date), date);
        const weekDay = timeDay.count(sourceData.timeFunction(date), date);
        const yearDay = timeDay.count(timeYear(date), date);
        dayMap.set(yearDay, { day, count, weekNumber, weekDay, date } as DayDatum);
        if (count > maxCount) {
            maxCount = count;
        }
    }

    if (!maxCount) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    // fill in any blanks
    const startDate = timeYear(nowForYear);
    const oneYearAgoFromNow = new Date(now);
    oneYearAgoFromNow.setFullYear(now.getFullYear() - 1);
    for (let i = 0; i < 365; i++) {
        const date = new Date(startDate.getTime() + i * 86400 * 1000);
        if (date > now) {
            // don't fill out future dates
            continue;
        }
        if (revlogRange == RevlogRange.Year && date < oneYearAgoFromNow) {
            // don't fill out dates older than a year
            continue;
        }
        const yearDay = timeDay.count(timeYear(date), date);
        if (!dayMap.has(yearDay)) {
            const weekNumber = sourceData.timeFunction.count(timeYear(date), date);
            const weekDay = timeDay.count(sourceData.timeFunction(date), date);
            dayMap.set(yearDay, {
                day: yearDay,
                count: 0,
                weekNumber,
                weekDay,
                date,
            } as DayDatum);
        }
    }
    const data = Array.from(dayMap.values());
    const cappedRange = scaleLinear().range([0.2, nightMode ? 0.8 : 1]);
    const blues = scaleSequentialSqrt()
        .domain([0, maxCount])
        .interpolator((n) => interpolateBlues(cappedRange(n)!));

    function tooltipText(d: DayDatum): string {
        const date = d.date.toLocaleString(i18n.langs, {
            weekday: "long",
            year: "numeric",
            month: "long",
            day: "numeric",
        });
        const cards = i18n.tr(i18n.TR.STATISTICS_REVIEWS, { reviews: d.count });
        return `${date}<br>${cards}`;
    }

    const height = bounds.height / 10;
    const emptyColour = nightMode ? "#333" : "#ddd";

    svg.select("g.weekdays")
        .selectAll("text")
        .data(sourceData.weekdayLabels)
        .join("text")
        .text((d: number) => i18n.weekdayLabel(d))
        .attr("width", x(-1)! - 2)
        .attr("height", height - 2)
        .attr("x", x(1)! - 3)
        .attr("y", (_d, index) => bounds.marginTop + index * height)
        .attr("fill", nightMode ? "#ddd" : "black")
        .attr("dominant-baseline", "hanging")
        .attr("text-anchor", "end")
        .attr("font-size", "small")
        .attr("font-family", "monospace")
        .style("user-select", "none")
        .on("click", null)
        .filter((d: number) =>
            [Weekday.SUNDAY, Weekday.MONDAY, Weekday.FRIDAY, Weekday.SATURDAY].includes(
                d
            )
        )
        .on("click", setFirstDayOfWeek);

    svg.select("g.days")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("fill", emptyColour)
        .attr("width", (d) => x(d.weekNumber + 1)! - x(d.weekNumber)! - 2)
        .attr("height", height - 2)
        .attr("x", (d) => x(d.weekNumber + 1)!)
        .attr("y", (d) => bounds.marginTop + d.weekDay * height)
        .on("mousemove", (event: MouseEvent, d: DayDatum) => {
            const [x, y] = pointer(event, document.body);
            showTooltip(tooltipText(d), x, y);
        })
        .on("mouseout", hideTooltip)
        .attr("class", (d: any): string => {
            return d.count > 0 ? "clickable" : "";
        })
        .on("click", function (_event: MouseEvent, d: any) {
            if (d.count > 0) {
                dispatch("search", { query: `"prop:rated=${d.day}"` });
            }
        })
        .transition()
        .duration(800)
        .attr("fill", (d) => (d.count === 0 ? emptyColour : blues(d.count)!));
}
