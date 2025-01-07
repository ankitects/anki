// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fabric } from "fabric";

import { TEXT_BACKGROUND_COLOR, TEXT_COLOR, TEXT_FONT_FAMILY, TEXT_FONT_SIZE, TEXT_PADDING } from "../tools/lib";
import type { ConstructorParams, Size } from "../types";
import type { ShapeDataForCloze } from "./base";
import { Shape } from "./base";
import { floatToDisplay } from "./floats";

export class Text extends Shape {
    text: string;
    scaleX: number;
    scaleY: number;
    fontSize: number | undefined;

    constructor({
        text = "",
        scaleX = 1,
        scaleY = 1,
        fontSize,
        ...rest
    }: ConstructorParams<Text> = {}) {
        super(rest);
        this.fill = TEXT_COLOR;
        this.text = text;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.fontSize = fontSize;
    }

    toDataForCloze(): TextDataForCloze {
        return {
            ...super.toDataForCloze(),
            text: this.text,
            // scaleX and scaleY are guaranteed to be equal since we lock the aspect ratio
            scale: floatToDisplay(this.scaleX),
            fs: this.fontSize ? floatToDisplay(this.fontSize) : undefined,
        };
    }

    toFabric(size: Size): fabric.IText {
        const absolute = this.toAbsolute(size);
        return new fabric.IText(absolute.text, {
            ...absolute,
            fontFamily: TEXT_FONT_FAMILY,
            backgroundColor: TEXT_BACKGROUND_COLOR,
            padding: TEXT_PADDING,
            lineHeight: 1,
            lockScalingFlip: true,
        });
    }

    toNormal(size: Size): Text {
        const fontSize = this.fontSize ? this.fontSize : TEXT_FONT_SIZE;
        return new Text({
            ...this,
            fontSize: fontSize / size.height,
            ...super.normalPosition(size),
        });
    }

    toAbsolute(size: Size): Text {
        return new Text({
            ...this,
            fontSize: this.fontSize ? this.fontSize * size.height : TEXT_FONT_SIZE,
            ...super.absolutePosition(size),
        });
    }
}

interface TextDataForCloze extends ShapeDataForCloze {
    text: string;
    scale: string;
    fs: string | undefined;
}
