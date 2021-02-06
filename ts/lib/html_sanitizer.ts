import sanitizeHtml from "sanitize-html";

export function basicHtml(html: string): string {
    return sanitizeHtml(html, {
        allowedTags: ["b", "i", "em", "strong", "a", "img", "div", "br"],
        allowedAttributes: {
            a: ["href"],
            img: ["src"],
        },
    });
}
