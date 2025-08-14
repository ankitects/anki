// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export type Size = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12;
export type Breakpoint = "xs" | "sm" | "md" | "lg" | "xl" | "xxl";

export type HelpItem = {
    title: string;
    help?: string;
    url?: string;
    sched?: HelpItemScheduler;
    global?: boolean;
};

export enum HelpItemScheduler {
    SM2 = 0,
    FSRS = 1,
}

export type IconData = {
    url: string;
};
