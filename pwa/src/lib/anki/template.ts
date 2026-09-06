export type AnkiField = {
  name: string;
  ord?: number | null;
  [key: string]: unknown;
};

export type AnkiTemplate = {
  name: string;
  ord?: number | null;
  qfmt: string;
  afmt: string;
  [key: string]: unknown;
};

export type AnkiNotetype = {
  id: number;
  name: string;
  type: number;
  flds: AnkiField[];
  tmpls: AnkiTemplate[];
  css: string;
  req?: Array<[number, "any" | "all" | "none", number[]]>;
  [key: string]: unknown;
};

export type RenderedCard = {
  questionHtml: string;
  answerHtml: string;
  css: string;
  typedAnswer?: {
    field: string;
    correct: string;
  };
};

type RenderContext = {
  fields: Map<string, string>;
  cardOrdinal: number;
  side: "question" | "answer";
  frontSide: string;
  typedAnswer?: RenderedCard["typedAnswer"];
};

const CLOZE_PATTERN = /{{c(\d+)::([\s\S]*?)(?:::(.*?))?}}/gi;

function stripHtml(value: string) {
  return value
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(?:div|p|li)>/gi, "\n")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<[^>]+>/g, "")
    .replace(/&#x([\da-f]+);|&#(\d+);|&(amp|lt|gt|quot|nbsp|#39);/gi,
      (entity, hex: string | undefined, decimal: string | undefined, named: string | undefined) => {
        const codepoint = hex ? Number.parseInt(hex, 16) : decimal ? Number(decimal) : undefined;
        if (codepoint !== undefined) {
          try { return String.fromCodePoint(codepoint); } catch { return entity; }
        }
        return ({ amp: "&", lt: "<", gt: ">", quot: '"', nbsp: " ", "#39": "'" } as Record<string, string>)[named?.toLowerCase() ?? ""] ?? entity;
      });
}

function renderClozes(value: string, ordinal: number, side: RenderContext["side"], only: boolean) {
  const target = ordinal + 1;
  const selected: string[] = [];
  const rendered = value.replace(CLOZE_PATTERN, (_match, numberText: string, answer: string, hint?: string) => {
    const number = Number(numberText);
    if (number !== target) return only ? "" : answer;

    const replacement = side === "question"
      ? `[${hint?.trim() || "..."}]`
      : answer;
    const cloze = `<span class="cloze" data-cloze="${number}">${replacement}</span>`;
    selected.push(cloze);
    return only ? "" : cloze;
  });

  return only ? selected.join(" ") : rendered;
}

function fieldIsPresent(fields: Map<string, string>, name: string) {
  return stripHtml(fields.get(name) ?? "").trim().length > 0;
}

function renderConditionals(template: string, fields: Map<string, string>) {
  let output = template;
  let previous = "";

  // Repeating allows nested sections to collapse from the inside out.
  while (output !== previous) {
    previous = output;
    output = output.replace(/{{([#^])([^{}]+)}}([\s\S]*?){{\/\2}}/g, (_match, kind: string, name: string, body: string) => {
      const present = fieldIsPresent(fields, name.trim());
      return (kind === "#" ? present : !present) ? body : "";
    });
  }

  return output;
}

function applyFilter(filter: string, value: string, context: RenderContext) {
  switch (filter.toLowerCase()) {
    case "text":
      return stripHtml(value);
    case "cloze":
      return renderClozes(value, context.cardOrdinal, context.side, false);
    case "cloze-only":
      return renderClozes(value, context.cardOrdinal, context.side, true);
    case "hint":
      return value.trim()
        ? `<span class="hint" title="${stripHtml(value).trim()}">Show hint</span>`
        : "";
    case "type":
      return context.side === "question"
        ? '<span class="type-answer-marker">Type the answer below</span>'
        : `<span class="type-answer-correct">${escapeHtml(stripHtml(value).trim())}</span>`;
    default:
      // Unknown add-on filters should not expose the raw template marker.
      return value;
  }
}

function escapeHtml(value: string) {
  return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

type OcclusionShape = {
  ordinal: number;
  type: "rect" | "ellipse" | "polygon";
  properties: Record<string, string>;
};

function parseOcclusionProperties(text: string) {
  const properties: Record<string, string> = {};
  let key = "";
  let value = "";
  let readingValue = false;
  let escaped = false;
  const commit = () => {
    if (key) properties[key] = value;
    key = ""; value = ""; readingValue = false;
  };
  for (const character of `${text}:`) {
    if (escaped) {
      if (readingValue) value += character;
      else key += character;
      escaped = false;
      continue;
    }
    if (character === "\\") { escaped = true; continue; }
    if (!readingValue && character === "=") { readingValue = true; continue; }
    if (character === ":") { commit(); continue; }
    if (readingValue) value += character;
    else key += character;
  }
  return properties;
}

export function parseImageOcclusions(value: string): OcclusionShape[] {
  const shapes: OcclusionShape[] = [];
  for (const match of value.matchAll(/{{c(\d+)::image-occlusion:(rect|ellipse|polygon):((?:\\.|[^{}])*)}}/gi)) {
    const ordinal = Number(match[1]);
    if (ordinal > 0) shapes.push({ ordinal, type: match[2].toLowerCase() as OcclusionShape["type"],
      properties: parseOcclusionProperties(match[3]) });
  }
  return shapes;
}

function safeRatio(value: string | undefined) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.min(1, Math.max(0, number)) : 0;
}

function occlusionStyle(shape: OcclusionShape) {
  const properties = shape.properties;
  const left = safeRatio(properties.left) * 100;
  const top = safeRatio(properties.top) * 100;
  if (shape.type === "polygon") {
    const points = (properties.points ?? "").split(" ").flatMap((point) => {
      const [x, y] = point.split(",").map(Number);
      return Number.isFinite(x) && Number.isFinite(y) ? [`${safeRatio(String(x)) * 100}% ${safeRatio(String(y)) * 100}%`] : [];
    });
    return `left:${left}%;top:${top}%;width:100%;height:100%;clip-path:polygon(${points.join(",")})`;
  }
  const width = safeRatio(properties.width ?? String(Number(properties.rx) * 2)) * 100;
  const height = safeRatio(properties.height ?? String(Number(properties.ry) * 2)) * 100;
  return `left:${left}%;top:${top}%;width:${width}%;height:${height}%${shape.type === "ellipse" ? ";border-radius:50%" : ""}`;
}

function isImageOcclusion(notetype: AnkiNotetype) {
  return Number(notetype.originalStockKind) === 6
    || (notetype.type === 1 && notetype.flds[0]?.name.toLowerCase().includes("occlusion")
      && notetype.flds[1]?.name.toLowerCase() === "image");
}

function renderImageOcclusion(notetype: AnkiNotetype, values: string[], cardOrdinal: number): RenderedCard {
  const fieldsByTag = new Map(notetype.flds.map((field, index) => [Number(field.tag ?? index), values[field.ord ?? index] ?? ""]));
  const occlusions = fieldsByTag.get(0) ?? values[0] ?? "";
  const image = fieldsByTag.get(1) ?? values[1] ?? "";
  const header = fieldsByTag.get(2) ?? values[2] ?? "";
  const backExtra = fieldsByTag.get(3) ?? values[3] ?? "";
  const target = cardOrdinal + 1;
  const masks = parseImageOcclusions(occlusions).flatMap((shape) => {
    const active = shape.ordinal === target;
    const visible = active || shape.properties.oi === "1";
    if (!visible) return [];
    return [`<span class="io-mask ${active ? "io-active" : "io-inactive"}" style="${occlusionStyle(shape)}"></span>`];
  }).join("");
  const imageWithMasks = `<div class="io-container">${image}<span class="io-mask-layer">${masks}</span></div>`;
  const questionHtml = `${header ? `<div class="io-header">${header}</div>` : ""}${imageWithMasks}`;
  const answerMasks = parseImageOcclusions(occlusions).flatMap((shape) => {
    const active = shape.ordinal === target;
    if (!active && shape.properties.oi !== "1") return [];
    return [`<span class="io-mask ${active ? "io-highlight" : "io-inactive"}" style="${occlusionStyle(shape)}"></span>`];
  }).join("");
  const answerHtml = `${header ? `<div class="io-header">${header}</div>` : ""}<div class="io-container">${image}<span class="io-mask-layer">${answerMasks}</span></div>${backExtra ? `<div class="io-back-extra">${backExtra}</div>` : ""}`;
  const css = `${notetype.css ?? ""}\n.io-container{position:relative;display:inline-block;max-width:100%;line-height:0}.io-container img{display:block;max-width:100%;height:auto}.io-mask-layer{position:absolute;inset:0}.io-mask{position:absolute;display:block;box-sizing:border-box}.io-active{background:#ff8e8e;border:1px solid #212121}.io-inactive{background:#ffeba2;border:1px solid #212121}.io-highlight{background:transparent;border:2px solid #ff5d5d}.io-header,.io-back-extra{margin:0 0 16px;line-height:1.45}.io-back-extra{margin:16px 0 0}`;
  return { questionHtml, answerHtml, css };
}

function typedAnswerForTemplate(template: string, fields: Map<string, string>): RenderedCard["typedAnswer"] {
  for (const match of template.matchAll(/{{([^{}]+)}}/g)) {
    const parts = match[1].split(":").map((part) => part.trim()).filter(Boolean);
    if (!parts.slice(0, -1).some((part) => part.toLowerCase() === "type")) continue;
    const field = parts.at(-1) ?? "";
    return { field, correct: stripHtml(fields.get(field) ?? "").trim() };
  }
  return undefined;
}

function renderTemplate(template: string, context: RenderContext) {
  const conditional = renderConditionals(template, context.fields);

  return conditional.replace(/{{([^{}]+)}}/g, (_match, expression: string) => {
    const parts = expression.split(":").map((part) => part.trim()).filter(Boolean);
    const fieldName = parts.pop() ?? "";
    let value = fieldName === "FrontSide"
      ? context.frontSide
      : context.fields.get(fieldName) ?? "";

    for (const filter of parts.reverse()) {
      value = applyFilter(filter, value, context);
    }
    return value;
  });
}

export function clozeOrdinals(fields: string[]) {
  const ordinals = new Set<number>();
  for (const field of fields) {
    for (const match of field.matchAll(new RegExp(CLOZE_PATTERN.source, CLOZE_PATTERN.flags))) {
      const clozeNumber = Number(match[1]);
      if (clozeNumber > 0) ordinals.add(clozeNumber - 1);
    }
  }
  return [...ordinals].sort((a, b) => a - b);
}

export function renderAnkiCard(notetype: AnkiNotetype, values: string[], cardOrdinal: number): RenderedCard {
  if (isImageOcclusion(notetype)) return renderImageOcclusion(notetype, values, cardOrdinal);
  const fields = new Map<string, string>();
  notetype.flds.forEach((field, index) => {
    fields.set(field.name, values[field.ord ?? index] ?? "");
  });

  const template = notetype.type === 1
    ? notetype.tmpls[0]
    : notetype.tmpls.find((candidate, index) => (candidate.ord ?? index) === cardOrdinal);
  if (!template) throw new Error(`Card template ${cardOrdinal + 1} is missing from ${notetype.name}`);
  const typedAnswer = typedAnswerForTemplate(template.qfmt, fields);

  const questionHtml = renderTemplate(template.qfmt, {
    fields,
    cardOrdinal,
    side: "question",
    frontSide: ""
  });
  const answerHtml = renderTemplate(template.afmt, {
    fields,
    cardOrdinal,
    side: "answer",
    frontSide: questionHtml
  });

  return { questionHtml, answerHtml, css: notetype.css ?? "", typedAnswer };
}
