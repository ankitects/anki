// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function getBrowserClasses(): string {
    const ua = navigator.userAgent.toLowerCase();
    const classes: string[] = [];

    function addBodyClass(className: string) {
        classes.push(className);
    }

    function test(regex: RegExp): boolean {
        return regex.test(ua);
    }

    if (test(/ipad/)) {
        addBodyClass("ipad");
    } else if (test(/iphone/)) {
        addBodyClass("iphone");
    } else if (test(/android/)) {
        addBodyClass("android");
    }

    if (test(/ipad|iphone|ipod/)) {
        addBodyClass("ios");
    }

    if (test(/ipad|iphone|ipod|android/)) {
        addBodyClass("mobile");
    }

    if (test(/linux/)) {
        addBodyClass("linux");
    } else if (test(/windows/)) {
        addBodyClass("win");
    } else if (test(/mac/)) {
        addBodyClass("mac");
    }

    if (test(/firefox\//)) {
        addBodyClass("firefox");
    } else if (test(/chrome\//)) {
        addBodyClass("chrome");
    } else if (test(/safari\//)) {
        addBodyClass("safari");
    }

    return classes.join(" ");
}
