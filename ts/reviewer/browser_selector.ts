// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function addBrowserClasses() {
    const ua = navigator.userAgent.toLowerCase();

    function addClass(className: string) {
        document.documentElement.classList.add(className);
    }

    function test(regex: RegExp): boolean {
        return regex.test(ua);
    }

    if (test(/ipad/)) {
        addClass("ipad");
    } else if (test(/iphone/)) {
        addClass("iphone");
    } else if (test(/android/)) {
        addClass("android");
    }

    if (test(/ipad|iphone|ipod/)) {
        addClass("ios");
    }

    if (test(/ipad|iphone|ipod|android/)) {
        addClass("mobile");
    } else if (test(/linux/)) {
        addClass("linux");
    } else if (test(/windows/)) {
        addClass("win");
    } else if (test(/mac/)) {
        addClass("mac");
    }

    if (test(/firefox\//)) {
        addClass("firefox");
    } else if (test(/chrome\//)) {
        addClass("chrome");
    } else if (test(/safari\//)) {
        addClass("safari");
    }
}
