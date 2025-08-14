// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { GraphsResponse } from "@generated/anki/stats_pb";
import { GraphPreferences_Weekday as Weekday } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { firstLanguage } from "@generated/ftl";
import { localizedDate, weekdayLabel } from "@tslib/i18n";
import type { CountableTimeInterval } from "d3";
import { timeHour } from "d3";
import {
    interpolateBlues,
    pointer,
    scaleLinear,
    scaleSequentialSqrt,
    select,
    timeDay,
    timeFriday,
    timeMonday,
    timeSaturday,
    timeSunday,
    timeYear,
    interpolateMagma
} from "d3";

import type { GraphBounds, SearchDispatch } from "./graph-helpers";
import { RevlogRange, setDataAvailable } from "./graph-helpers";
import { clickableClass } from "./graph-styles";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

export interface GraphData {
    // indexed by day, where day is relative to today
    reviewCount: Map<number, number>;
    timeFunction: CountableTimeInterval;
    weekdayLabels: number[];
    rolloverHour: number;
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

export function gatherData(
    data: GraphsResponse,
    firstDayOfWeek: Weekday,
): GraphData {
    const reviewCount = new Map(
        Object.entries(data.reviews!.count).map(([k, v]) => {
            return [Number(k), v.learn + v.relearn + v.mature + v.filtered + v.young];
        }),
    );
    const timeFunction = timeFunctionForDay(firstDayOfWeek);
    const weekdayLabels: number[] = [];
    for (let i = 0; i < 7; i++) {
        weekdayLabels.push((firstDayOfWeek + i) % 7);
    }

    return { reviewCount, timeFunction, weekdayLabels, rolloverHour: data.rolloverHour };
}

export function renderCalendar(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData,
    dispatch: SearchDispatch,
    targetYear: number,
    nightMode: boolean,
    revlogRange: RevlogRange,
    setFirstDayOfWeek: (d: number) => void,
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
        let date = timeDay.offset(now, day);
        // anki day does not necessarily roll over at midnight, we account for this when mapping onto calendar days
        date = timeHour.offset(date, -1 * sourceData.rolloverHour);
        if (count > maxCount) {
            maxCount = count;
        }
        if (date.getFullYear() != targetYear) {
            continue;
        }
        const weekNumber = sourceData.timeFunction.count(timeYear(date), date);
        const weekDay = timeDay.count(sourceData.timeFunction(date), date);
        const yearDay = timeDay.count(timeYear(date), date);
        dayMap.set(yearDay, { day, count, weekNumber, weekDay, date });
    }

    if (!maxCount) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    // fill in any blanks, including the current calendar day even if the anki day has not rolled over
    const startDate = timeYear(nowForYear);
    const oneYearAgoFromNow = new Date(now);
    oneYearAgoFromNow.setFullYear(now.getFullYear() - 1);
    for (let i = 0; i < 365; i++) {
        const date = timeDay.offset(startDate, i);
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
            });
        }
    }
    const data = Array.from(dayMap.values());
    const cappedRange = scaleLinear().range([1,0]);

    const isColorBlindMode = (window as any).colorBlindMode;


    let gradient;

    if (isColorBlindMode) {
        gradient = scaleSequentialSqrt()
        .domain([0, maxCount])
        .interpolator((n) => interpolateMagma(cappedRange(n)!));
    }   else {
        gradient = scaleSequentialSqrt()
        .domain([0, maxCount])
        .interpolator((n) => interpolateBlues(cappedRange(n)!));
    }

    function tooltipText(d: DayDatum): string {
        const date = localizedDate(d.date, {
            weekday: "long",
            year: "numeric",
            month: "long",
            day: "numeric",
        });
        const cards = tr.statisticsReviews({ reviews: d.count });
        return `${date}<br>${cards}`;
    }

    const height = bounds.height / 10;
    const emptyColour = nightMode ? "#333" : "#ddd";

    const firstLang = firstLanguage();

    svg.select("g.weekdays")
        .selectAll("text")
        .data(sourceData.weekdayLabels)
        .join("text")
        .text((d: number) => weekdayLabel(d))
        .attr("width", x(-1)! - 2)
        .attr("height", height - 2)
        .attr("x", x(1)! - 3)
        .attr("y", (_d, index) => bounds.marginTop + index * height)
        .attr("fill", nightMode ? "#ddd" : "black")
        .attr("dominant-baseline", "hanging")
        .attr("text-anchor", "end")
        .attr("font-size", firstLang.includes("zh") ? "xx-small" : "small")
        .attr("font-family", "monospace")
        .attr("direction", "ltr")
        .style("user-select", "none")
        .on("click", null)
        .filter((d: number) =>
            [Weekday.SUNDAY, Weekday.MONDAY, Weekday.FRIDAY, Weekday.SATURDAY].includes(
                d,
            )
        )
        .on("click", (_event: MouseEvent, d: number) => setFirstDayOfWeek(d));

    svg.select("g.days")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("fill", emptyColour)
        .attr("width", (d: DayDatum) => x(d.weekNumber + 1)! - x(d.weekNumber)! - 2)
        .attr("height", height - 2)
        .attr("x", (d: DayDatum) => x(d.weekNumber + 1)!)
        .attr("y", (d: DayDatum) => bounds.marginTop + d.weekDay * height)
        .on("mousemove", (event: MouseEvent, d: DayDatum) => {
            const [x, y] = pointer(event, document.body);
            showTooltip(tooltipText(d), x, y);
        })
        .on("mouseout", hideTooltip)
        .attr("class", (d: DayDatum): string => (d.count > 0 ? clickableClass : ""))
        .on("click", function(_event: MouseEvent, d: DayDatum) {
            if (d.count > 0) {
                dispatch("search", { query: `"prop:rated=${d.day}"` });
            }
        })
        .transition()
        .duration(800)
        .attr("fill", (d: DayDatum) => (d.count === 0 ? emptyColour : gradient(d.count)!));
}

function timeFunctionForDay(firstDayOfWeek: Weekday): CountableTimeInterval {
    switch (firstDayOfWeek) {
        case Weekday.MONDAY:
            return timeMonday;
        case Weekday.FRIDAY:
            return timeFriday;
        case Weekday.SATURDAY:
            return timeSaturday;
        default:
            return timeSunday;
    }
}
