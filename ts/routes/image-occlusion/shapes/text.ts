// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { TEXT_BACKGROUND_COLOR, TEXT_FONT_FAMILY, TEXT_PADDING } from "../tools/lib";
import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";

export class Text extends Shape {
    text: string;
    scaleX: number;
    scaleY: number;

    constructor({
        text = "",
        scaleX = 1,
        scaleY = 1,
        ...rest
    }: ConstructorParams<Text> = {}) {
        super(rest);
        this.text = text;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    toDataForCloze(): TextDataForCloze {
        return {
            ...super.toDataForCloze(),
            text: this.text,
            // scaleX and scaleY are guaranteed to be equal since we lock the aspect ratio
            scale: floatToDisplay(this.scaleX),
        };
    }

    toFabric(size: Size): fabric.IText {
        const absolute = this.toAbsolute(size);
        return new fabric.IText(absolute.text, {
            ...absolute,
            fontFamily: TEXT_FONT_FAMILY,
            backgroundColor: TEXT_BACKGROUND_COLOR,
            padding: TEXT_PADDING,
        });
    }

    toNormal(size: Size): Text {
        return new Text({
            ...this,
            ...super.normalPosition(size),
        });
    }

    toAbsolute(size: Size): Text {
        return new Text({
            ...this,
            ...super.absolutePosition(size),
        });
    }
}

interface TextDataForCloze extends ShapeDataForCloze {
    text: string;
    scale: string;
}
