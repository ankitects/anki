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
};

type RenderContext = {
  fields: Map<string, string>;
  cardOrdinal: number;
  side: "question" | "answer";
  frontSide: string;
};

const CLOZE_PATTERN = /{{c(\d+)::([\s\S]*?)(?:::(.*?))?}}/gi;

function stripHtml(value: string) {
  return value
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/gi, " ");
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
    default:
      // Unknown add-on filters should not expose the raw template marker.
      return value;
  }
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
  const fields = new Map<string, string>();
  notetype.flds.forEach((field, index) => {
    fields.set(field.name, values[field.ord ?? index] ?? "");
  });

  const template = notetype.type === 1
    ? notetype.tmpls[0]
    : notetype.tmpls.find((candidate, index) => (candidate.ord ?? index) === cardOrdinal);
  if (!template) throw new Error(`Card template ${cardOrdinal + 1} is missing from ${notetype.name}`);

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

  return { questionHtml, answerHtml, css: notetype.css ?? "" };
}
