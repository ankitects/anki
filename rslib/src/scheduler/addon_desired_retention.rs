// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Mobile evaluation of the Dynamic Desired Retention add-on's synced rules.

use std::collections::HashMap;
use std::collections::HashSet;
use std::sync::Arc;
use std::sync::LazyLock;

use regex::Regex;
use serde_json::Value;

use crate::card::Card;
use crate::prelude::*;
use crate::search::SortMode;
use crate::text::decode_entities;

pub(crate) const CONFIG_KEY: &str = "dynamicDesiredRetentionRules";
static HTML_TAG: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r#"(?s)<!--.*?-->|<(?:(?:"[^"]*")|(?:'[^']*')|[^'">])*?>"#).unwrap()
});
static RELATIVE_THRESHOLD: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)^(?:top\s*)?([0-9]+(?:\.[0-9]+)?)\s*%?$").unwrap());

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) struct RetentionMatch {
    pub retention: f32,
    pub minimum_interval: Option<u32>,
    pub maximum_interval: Option<u32>,
}

#[derive(Debug, Clone, Copy)]
struct GradeRule {
    relative: bool,
    value: f64,
}

#[derive(Debug, Default)]
pub(crate) struct AddonRetentionRules {
    searches: Vec<(String, f64)>,
    fields: Vec<FieldRule>,
    grades: [Option<GradeRule>; 4],
}

#[derive(Debug)]
struct FieldRule {
    note_type: String,
    field: String,
    scope: String,
    exclusion: String,
    numeric: bool,
    relative: bool,
    higher: bool,
    linear: bool,
    thresholds: Vec<Threshold>,
    minimum: Option<RetentionMatch>,
}

#[derive(Debug, Clone)]
struct Threshold {
    text: String,
    number: Option<f64>,
    retention: f64,
    result: RetentionMatch,
}

fn text(value: Option<&Value>) -> Option<&str> {
    value?
        .as_str()
        .map(str::trim)
        .filter(|value| !value.is_empty())
}

fn valid_retention(value: Option<&Value>) -> Option<f64> {
    value?
        .as_f64()
        .filter(|value| *value > 0.0 && *value <= 1.0)
}

fn interval_bounds(value: &Value, min: &str, max: &str) -> Option<(Option<u32>, Option<u32>)> {
    let parse = |key: &str| match value.get(key) {
        None | Some(Value::Null) => Some(None),
        Some(value) => value
            .as_u64()
            .and_then(|value| u32::try_from(value).ok())
            .filter(|value| *value > 0)
            .map(Some),
    };
    let minimum = parse(min)?;
    let maximum = parse(max)?;
    if matches!((minimum, maximum), (Some(minimum), Some(maximum)) if minimum > maximum) {
        return None;
    }
    Some((minimum, maximum))
}

fn optional_string(value: &Value, key: &str) -> Option<String> {
    match value.get(key) {
        None => Some(String::new()),
        Some(value) => Some(value.as_str()?.trim().to_owned()),
    }
}

fn threshold_text(value: &Value, numeric: bool, relative: bool) -> Option<String> {
    let raw = match value {
        Value::String(value) => value.trim().to_owned(),
        Value::Number(value) if numeric => value.to_string(),
        _ => return None,
    };
    if raw.is_empty() {
        return None;
    }
    if !numeric {
        return Some(raw);
    }
    let numeric_text = if relative {
        RELATIVE_THRESHOLD.captures(&raw)?.get(1)?.as_str()
    } else {
        raw.as_str()
    };
    let number: f64 = numeric_text.parse().ok()?;
    if !number.is_finite() || (relative && (number <= 0.0 || number > 100.0)) {
        return None;
    }
    Some(numeric_text.to_owned())
}

impl FieldRule {
    fn parse(value: &Value) -> Option<Self> {
        let note_type = text(value.get("note_type"))?.to_owned();
        let field = text(value.get("field"))?.to_owned();
        let scope = optional_string(value, "scope_query")?;
        let exclusion = optional_string(value, "filter_query")?;
        let numeric = match value.get("value_type")?.as_str()? {
            "number" => true,
            "text" => false,
            _ => return None,
        };
        let relative = match value.get("field_value_mode").map(Value::as_str) {
            None | Some(Some("absolute")) => false,
            Some(Some("relative")) if numeric => true,
            _ => return None,
        };
        let higher = match value.get("relative_top_direction").map(Value::as_str) {
            None | Some(Some("lower")) => false,
            Some(Some("higher")) => true,
            _ => return None,
        };
        let linear = match value.get("linear_between_thresholds") {
            None => false,
            Some(value) => value.as_bool()?,
        };
        if linear && !numeric {
            return None;
        }
        let minimum_retention = match value.get("minimum_desired_retention") {
            None | Some(Value::Null) => None,
            Some(retention) if numeric => Some(valid_retention(Some(retention))?),
            _ => return None,
        };
        let (minimum_interval, maximum_interval) = interval_bounds(
            value,
            "minimum_retention_minimum_interval",
            "minimum_retention_maximum_interval",
        )?;
        if minimum_retention.is_none() && (minimum_interval.is_some() || maximum_interval.is_some())
        {
            return None;
        }
        let minimum = minimum_retention.map(|retention| RetentionMatch {
            retention: retention as f32,
            minimum_interval,
            maximum_interval,
        });
        let mut thresholds = Vec::new();
        for threshold in value.get("thresholds")?.as_array()? {
            let text = threshold_text(threshold.get("value")?, numeric, relative)?;
            let number = if numeric {
                Some(text.parse::<f64>().ok()?)
            } else {
                None
            };
            if thresholds.iter().any(|previous: &Threshold| {
                if numeric {
                    previous.number == number
                } else {
                    previous.text == text
                }
            }) {
                return None;
            }
            let retention = valid_retention(threshold.get("desired_retention"))?;
            let (minimum_interval, maximum_interval) =
                interval_bounds(threshold, "minimum_interval", "maximum_interval")?;
            thresholds.push(Threshold {
                text,
                number,
                retention,
                result: RetentionMatch {
                    retention: retention as f32,
                    minimum_interval,
                    maximum_interval,
                },
            });
        }
        if thresholds.is_empty() {
            return None;
        }
        if numeric {
            thresholds.sort_by(|a, b| a.number.unwrap().total_cmp(&b.number.unwrap()));
            let ordered: Vec<_> = if higher && !relative {
                thresholds.iter().rev().collect()
            } else {
                thresholds.iter().collect()
            };
            if ordered
                .windows(2)
                .any(|pair| pair[1].retention > pair[0].retention)
                || minimum_retention
                    .is_some_and(|minimum| minimum > ordered.last().unwrap().retention)
            {
                return None;
            }
        }
        Some(Self {
            note_type,
            field,
            scope,
            exclusion,
            numeric,
            relative,
            higher,
            linear,
            thresholds,
            minimum,
        })
    }

    fn match_text(&self, raw: &str, population: &[f64]) -> Option<RetentionMatch> {
        let raw = raw.trim();
        if !self.numeric {
            return self
                .thresholds
                .iter()
                .find(|threshold| threshold.text == raw)
                .map(|threshold| threshold.result);
        }
        let value = numeric_field(raw)?;
        let value = if self.relative {
            if population.is_empty() {
                return None;
            }
            let count = if self.higher {
                population.len() - population.partition_point(|other| *other < value)
            } else {
                population.partition_point(|other| *other <= value)
            };
            count as f64 / population.len() as f64 * 100.0
        } else {
            value
        };
        let higher = self.higher && !self.relative;
        let first = self.thresholds.first()?;
        let last = self.thresholds.last()?;
        if value < first.number? {
            return Some(if higher {
                self.minimum.unwrap_or(first.result)
            } else {
                first.result
            });
        }
        if value > last.number? {
            return Some(if higher {
                last.result
            } else {
                self.minimum.unwrap_or(last.result)
            });
        }
        if let Some(threshold) = self
            .thresholds
            .iter()
            .find(|threshold| threshold.number == Some(value))
        {
            return Some(threshold.result);
        }
        if self.linear {
            for pair in self.thresholds.windows(2) {
                let (lower, upper) = (&pair[0], &pair[1]);
                let (lo, hi) = (lower.number?, upper.number?);
                if lo < value && value < hi {
                    let ratio = (value - lo) / (hi - lo);
                    let retention = lower.retention + ratio * (upper.retention - lower.retention);
                    let mut result = if higher { lower.result } else { upper.result };
                    result.retention = retention as f32;
                    return Some(result);
                }
            }
        } else if higher {
            return self
                .thresholds
                .iter()
                .rev()
                .find(|threshold| value >= threshold.number.unwrap())
                .map(|threshold| threshold.result);
        } else {
            return self
                .thresholds
                .iter()
                .find(|threshold| value <= threshold.number.unwrap())
                .map(|threshold| threshold.result);
        }
        None
    }
}

fn numeric_field(raw: &str) -> Option<f64> {
    let raw = raw.trim();
    if raw.is_empty() {
        return None;
    }
    let parts = if raw.contains('<') && raw.contains('>') {
        HTML_TAG
            .split(raw)
            .map(decode_entities)
            .map(|part| part.trim().to_owned())
            .filter(|part| !part.is_empty())
            .collect::<Vec<_>>()
            .join(" ")
    } else {
        raw.to_owned()
    };
    parts.parse::<f64>().ok().filter(|value| value.is_finite())
}

fn search_candidates(
    col: &mut Collection,
    query: &str,
    cards: &[Card],
    restrict_single_card: bool,
) -> Result<Vec<CardId>> {
    if restrict_single_card && cards.len() == 1 {
        // The reviewer asks about one card at a time. Avoid finding every
        // matching card in the collection just to test membership. Parentheses
        // preserve the original query's OR/AND precedence.
        let query = format!("({query}) cid:{}", cards[0].id);
        col.search_cards(&query, SortMode::NoOrder)
    } else {
        col.search_cards(query, SortMode::NoOrder)
    }
}

impl AddonRetentionRules {
    pub(crate) fn from_collection(col: &Collection) -> Option<Self> {
        let value: Value = col.get_config_optional(CONFIG_KEY)?;
        if value.get("version")?.as_u64() != Some(1) {
            return None;
        }
        let mut rules = Self::default();
        if let Some(searches) = value.get("rules").and_then(Value::as_array) {
            for search in searches {
                if let (Some(query), Some(retention)) = (
                    text(search.get("query")),
                    valid_retention(search.get("desired_retention")),
                ) {
                    rules.searches.push((query.to_owned(), retention));
                }
            }
        }
        if let Some(fields) = value.get("field_rules").and_then(Value::as_array) {
            rules
                .fields
                .extend(fields.iter().filter_map(FieldRule::parse));
        }
        if let Some(grades) = value.get("grade_rules").and_then(Value::as_array) {
            for grade in grades {
                let index = match grade.get("grade").and_then(Value::as_str) {
                    Some("again") => 0,
                    Some("hard") => 1,
                    Some("good") => 2,
                    Some("easy") => 3,
                    _ => continue,
                };
                let Some(value) = grade.get("value").and_then(Value::as_f64) else {
                    continue;
                };
                let relative = match grade.get("mode").and_then(Value::as_str) {
                    Some("absolute") if value > 0.0 && value <= 1.0 => false,
                    Some("relative") if value > -1.0 && value <= 1.0 => true,
                    _ => continue,
                };
                if rules.grades[index].is_none() {
                    rules.grades[index] = Some(GradeRule { relative, value });
                }
            }
        }
        Some(rules)
    }

    pub(crate) fn has_grade_rules(&self) -> bool {
        self.grades.iter().any(Option::is_some)
    }

    pub(crate) fn has_grade_rule(&self, index: usize) -> bool {
        self.grades.get(index).is_some_and(Option::is_some)
    }

    pub(crate) fn target_for_grade(&self, base: Option<f32>, index: usize) -> Option<f32> {
        match self.grades.get(index).copied().flatten() {
            None => base,
            Some(rule) if !rule.relative => Some(rule.value as f32),
            Some(rule) => base.map(|base| {
                let base = base as f64;
                (base + rule.value * if rule.value >= 0.0 { 1.0 - base } else { base }) as f32
            }),
        }
    }

    pub(crate) fn matches(
        &self,
        col: &mut Collection,
        cards: &[Card],
    ) -> Result<HashMap<CardId, RetentionMatch>> {
        let mut matches = HashMap::new();
        let mut pending: HashSet<_> = cards.iter().map(|card| card.id).collect();
        for (query, retention) in &self.searches {
            if pending.is_empty() {
                break;
            }
            match search_candidates(col, query, cards, true) {
                Ok(ids) => {
                    for id in ids {
                        if pending.remove(&id) {
                            matches.insert(
                                id,
                                RetentionMatch {
                                    retention: *retention as f32,
                                    minimum_interval: None,
                                    maximum_interval: None,
                                },
                            );
                        }
                    }
                }
                Err(error) => tracing::warn!(?error, "Dynamic DR search rule failed"),
            }
        }
        for rule in &self.fields {
            if pending.is_empty() {
                break;
            }
            let Some(notetype) = col.get_notetype_by_name(&rule.note_type)? else {
                continue;
            };
            if notetype.name != rule.note_type {
                continue;
            }
            let Some(field_index) = notetype
                .fields
                .iter()
                .position(|field| field.name == rule.field)
            else {
                continue;
            };
            let Ok(field_index) = u16::try_from(field_index) else {
                continue;
            };
            let scope = if rule.scope.is_empty() {
                None
            } else {
                match search_candidates(col, &rule.scope, cards, !rule.relative) {
                    Ok(ids) => Some(ids.into_iter().collect::<HashSet<_>>()),
                    Err(error) => {
                        tracing::warn!(?error, "Dynamic DR field scope failed");
                        continue;
                    }
                }
            };
            let exclusion = if rule.exclusion.is_empty() {
                HashSet::new()
            } else {
                match search_candidates(col, &rule.exclusion, cards, !rule.relative) {
                    Ok(ids) => ids.into_iter().collect(),
                    Err(error) => {
                        tracing::warn!(?error, "Dynamic DR field exclusion failed");
                        // The desktop add-on's percentile population search
                        // includes the exclusion query and fails as a whole.
                        if rule.relative {
                            continue;
                        }
                        HashSet::new()
                    }
                }
            };
            let allowed = |id: &CardId| {
                scope.as_ref().map_or(true, |scope| scope.contains(id)) && !exclusion.contains(id)
            };
            // Without a scope/exclusion search, the population depends only
            // on note fields and card/note membership, not on review answers.
            // Cache it until those collection objects or the rules change.
            let cache_key = (rule.relative && rule.scope.is_empty() && rule.exclusion.is_empty())
                .then_some((notetype.id, field_index));
            let cached_population =
                cache_key.and_then(|key| col.state.addon_retention_populations.get(&key).cloned());
            let population_cards = if rule.relative && cached_population.is_none() {
                let query = format!("note:\"{}\"", rule.note_type.replace('"', "\\\""));
                let ids: Vec<_> = match col.search_cards(&query, SortMode::NoOrder) {
                    Ok(ids) => ids.into_iter().filter(allowed).collect(),
                    Err(error) => {
                        tracing::warn!(?error, "Dynamic DR percentile population failed");
                        continue;
                    }
                };
                col.all_cards_for_ids(&ids, false)?
            } else {
                Vec::new()
            };
            let note_ids: Vec<_> = cards
                .iter()
                .filter(|card| pending.contains(&card.id) && allowed(&card.id))
                .chain(population_cards.iter())
                .map(|card| card.note_id)
                .collect::<HashSet<_>>()
                .into_iter()
                .collect();
            let fields = col
                .storage
                .get_note_field_by_ids(&note_ids, notetype.id, field_index)?;
            let population = if let Some(population) = cached_population {
                population
            } else {
                let mut population: Vec<_> = population_cards
                    .iter()
                    .map(|card| card.note_id)
                    .collect::<HashSet<_>>()
                    .iter()
                    .filter_map(|nid| fields.get(nid))
                    .filter_map(|field| numeric_field(field))
                    .collect();
                population.sort_by(f64::total_cmp);
                let population = Arc::new(population);
                if let Some(key) = cache_key {
                    col.state
                        .addon_retention_populations
                        .insert(key, population.clone());
                }
                population
            };
            for card in cards
                .iter()
                .filter(|card| pending.contains(&card.id) && allowed(&card.id))
            {
                let Some(field) = fields.get(&card.note_id) else {
                    continue;
                };
                if let Some(result) = rule.match_text(field, &population) {
                    matches.insert(card.id, result);
                }
            }
            pending.retain(|id| !matches.contains_key(id));
        }
        Ok(matches)
    }
}

#[cfg(test)]
mod test {
    use serde_json::json;

    use super::*;

    #[test]
    fn numeric_rules_handle_thresholds_interpolation_percentiles_and_bounds() {
        let make_rule = |direction: &str, mode: &str, linear: bool| {
            FieldRule::parse(&json!({
                "note_type": "Basic", "field": "Front", "value_type": "number",
                "relative_top_direction": direction, "field_value_mode": mode,
                "linear_between_thresholds": linear,
                "minimum_desired_retention": 0.5,
                "thresholds": [
                    {"value": "10", "desired_retention": 0.9, "minimum_interval": 2},
                    {"value": "30", "desired_retention": 0.7, "maximum_interval": 20}
                ]
            }))
            .unwrap()
        };
        let linear = make_rule("lower", "absolute", true);
        assert_eq!(
            linear
                .match_text("<b>10</b>", &[])
                .unwrap()
                .minimum_interval,
            Some(2)
        );
        assert_eq!(
            linear
                .match_text("<span title='>'>10</span>", &[])
                .unwrap()
                .minimum_interval,
            Some(2)
        );
        assert_eq!(
            linear.match_text("30", &[]).unwrap().maximum_interval,
            Some(20)
        );
        assert!((linear.match_text("20", &[]).unwrap().retention - 0.8).abs() < 0.00001);
        assert_eq!(linear.match_text("31", &[]).unwrap().retention, 0.5);
        assert!(linear.match_text("<b>1</b><b>2</b>", &[]).is_none());

        let relative = make_rule("higher", "relative", false);
        // A value tied for the two highest of five notes is in the top 40%.
        assert_eq!(
            relative
                .match_text("10", &[1.0, 2.0, 3.0, 10.0, 10.0])
                .unwrap()
                .retention,
            0.5
        );
        assert_eq!(
            relative
                .match_text("10", &[1.0, 2.0, 3.0, 4.0, 10.0])
                .unwrap()
                .retention,
            0.7
        );
        assert!(FieldRule::parse(&json!({
            "note_type": "Basic", "field": "Front", "value_type": "number",
            "field_value_mode": "relative", "thresholds": [{"value": "Top 10%%", "desired_retention": 0.9}]
        })).is_none());
    }

    #[test]
    fn synced_rules_match_search_before_fields_and_apply_grade_rules() -> Result<()> {
        let mut col = Collection::new();
        let notetype = col.get_notetype_by_name("Basic")?.unwrap();
        let mut ids = Vec::new();
        for (value, tags) in [
            ("10", vec!["scope"]),
            ("20", vec!["scope", "urgent"]),
            ("30", vec!["excluded"]),
        ] {
            let mut note = notetype.new_note();
            note.set_field(0, value)?;
            note.tags = tags.into_iter().map(str::to_owned).collect();
            col.add_note(&mut note, DeckId(1))?;
            ids.push(note.id);
        }
        col.set_config(CONFIG_KEY, &json!({
            "version": 1,
            "rules": [{"query": "tag:urgent", "desired_retention": 0.95}],
            "field_rules": [{"note_type": "Basic", "field": "Front", "value_type": "number",
                "scope_query": "tag:scope", "filter_query": "tag:excluded",
                "thresholds": [{"value": "10", "desired_retention": 0.9}, {"value": "30", "desired_retention": 0.7}]}],
            "grade_rules": [{"grade": "again", "mode": "relative", "value": -0.2},
                {"grade": "easy", "mode": "absolute", "value": 0.99}]
        }))?;
        let card_ids = col.search_cards("deck:Default", SortMode::NoOrder)?;
        let cards = col.all_cards_for_ids(&card_ids, false)?;
        let rules = AddonRetentionRules::from_collection(&col).unwrap();
        let matches = rules.matches(&mut col, &cards)?;
        let card = |note_id| {
            cards
                .iter()
                .find(|card| card.note_id == note_id)
                .unwrap()
                .id
        };
        assert_eq!(matches[&card(ids[0])].retention, 0.9);
        assert_eq!(matches[&card(ids[1])].retention, 0.95);
        assert!(!matches.contains_key(&card(ids[2])));
        assert!(rules.has_grade_rules());
        assert!((rules.target_for_grade(Some(0.6), 0).unwrap() - 0.48).abs() < 0.00001);
        assert_eq!(rules.target_for_grade(None, 0), None);
        assert_eq!(rules.target_for_grade(None, 3), Some(0.99));
        col.set_config(CONFIG_KEY, &json!({"version": 2}))?;
        assert!(AddonRetentionRules::from_collection(&col).is_none());
        Ok(())
    }

    #[test]
    fn single_card_queries_preserve_or_precedence_and_invalid_percentile_filter_is_skipped(
    ) -> Result<()> {
        let mut col = Collection::new();
        let notetype = col.get_notetype_by_name("Basic")?.unwrap();
        for tag in ["first", "second", "other"] {
            let mut note = notetype.new_note();
            note.set_field(0, "10")?;
            note.tags.push(tag.to_owned());
            col.add_note(&mut note, DeckId(1))?;
        }
        let ids = col.search_cards("deck:Default", SortMode::NoOrder)?;
        let cards = col.all_cards_for_ids(&ids, false)?;
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "rules": [{"query": "tag:first OR tag:second", "desired_retention": 0.9}]
            }),
        )?;
        let rules = AddonRetentionRules::from_collection(&col).unwrap();
        let bulk = rules.matches(&mut col, &cards)?;
        assert_eq!(bulk.len(), 2);
        for card in &cards {
            let single = rules.matches(&mut col, std::slice::from_ref(card))?;
            assert_eq!(single.get(&card.id), bulk.get(&card.id));
        }

        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "field_rules": [{"note_type": "Basic", "field": "Front", "value_type": "number",
                    "field_value_mode": "relative", "filter_query": "(",
                    "thresholds": [{"value": "100", "desired_retention": 0.9}]}]
            }),
        )?;
        let rules = AddonRetentionRules::from_collection(&col).unwrap();
        assert!(rules.matches(&mut col, &cards)?.is_empty());
        Ok(())
    }

    #[test]
    fn relative_field_population_counts_formatted_notes_once() -> Result<()> {
        let mut col = Collection::new();
        let notetype = col
            .get_notetype_by_name("Basic (and reversed card)")?
            .unwrap();
        let mut middle_note = None;
        for (front, back) in [("<b>10</b>", ""), ("20", "back"), ("30", "")] {
            let mut note = notetype.new_note();
            note.set_field(0, front)?;
            note.set_field(1, back)?;
            col.add_note(&mut note, DeckId(1))?;
            if front == "20" {
                middle_note = Some(note.id);
            }
        }
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "field_rules": [{"note_type": "Basic (and reversed card)", "field": "Front",
                    "value_type": "number", "field_value_mode": "relative",
                    "thresholds": [{"value": "Top 70%", "desired_retention": 0.9},
                        {"value": "100", "desired_retention": 0.6}]}]
            }),
        )?;
        let ids = col.search_cards("deck:Default", SortMode::NoOrder)?;
        let cards = col.all_cards_for_ids(&ids, false)?;
        let middle: Vec<_> = cards
            .iter()
            .filter(|card| Some(card.note_id) == middle_note)
            .collect();
        assert_eq!(middle.len(), 2);
        let rules = AddonRetentionRules::from_collection(&col).unwrap();
        let matches = rules.matches(&mut col, &cards)?;
        for card in &middle {
            assert_eq!(matches[&card.id].retention, 0.9);
        }
        let cached = col
            .state
            .addon_retention_populations
            .get(&(notetype.id, 0))
            .unwrap()
            .clone();
        rules.matches(&mut col, &cards)?;
        assert!(Arc::ptr_eq(
            &cached,
            &col.state.addon_retention_populations[&(notetype.id, 0)]
        ));
        let mut note = col.storage.get_note(middle_note.unwrap())?.unwrap();
        note.set_field(0, "100")?;
        col.update_note(&mut note)?;
        assert!(col.state.addon_retention_populations.is_empty());
        let changed = rules.matches(&mut col, &cards)?;
        for card in middle {
            assert_eq!(changed[&card.id].retention, 0.6);
        }
        Ok(())
    }

    #[test]
    fn synced_rules_drive_review_buttons_answers_and_rwkv_targets() -> Result<()> {
        use anki_proto::scheduler::RwkvReviewInputRowsForCardsRequest;

        use crate::config::BoolKey;
        use crate::scheduler::states::CardState;
        use crate::scheduler::states::NormalState;

        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        let notetype = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = notetype.new_note();
        note.set_field(0, "10")?;
        col.add_note(&mut note, DeckId(1))?;
        let card_id = col.answer_easy().card_id;
        col.set_due_date(&[card_id], "0", None)?;
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "field_rules": [{"note_type": "Basic", "field": "Front", "value_type": "number",
                    "thresholds": [{"value": "10", "desired_retention": 0.9,
                        "minimum_interval": 10, "maximum_interval": 20}]}],
                "grade_rules": [{"grade": "good", "mode": "absolute", "value": 0.6}]
            }),
        )?;

        let choices = col.get_scheduling_states(card_id)?;
        let CardState::Normal(NormalState::Review(good)) = choices.good else {
            panic!("expected a review state");
        };
        assert!((10..=20).contains(&good.scheduled_days));
        let rows = col.rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
            card_ids: vec![card_id.0],
            include_disabled_decks: true,
            ..Default::default()
        })?;
        assert_eq!(rows.rows.len(), 1);
        assert_eq!(rows.rows[0].target_retention, 0.9);
        col.answer_good();
        let answered = col.get_first_card();
        assert_eq!(answered.desired_retention, Some(0.6));
        assert!((10..=20).contains(&answered.interval));
        Ok(())
    }

    #[test]
    fn grade_target_with_native_dynamic_dr_uses_the_exact_grade_retention() -> Result<()> {
        use crate::config::BoolKey;
        use crate::scheduler::answering::CardAnswer;
        use crate::scheduler::answering::Rating;
        use crate::scheduler::states::CardState;
        use crate::scheduler::states::NormalState;

        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_dynamic_desired_retention_enabled = true;
            config.fsrs_dynamic_desired_retention_params = vec![0.0; 15];
            config.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
            config.fsrs_dynamic_desired_retention_avg_drs = vec![0.8, 0.9];
            config.fsrs_dynamic_desired_retention_min = 0.75;
            config.fsrs_dynamic_desired_retention_max = 0.95;
        });
        let notetype = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = notetype.new_note();
        note.set_field(0, "example")?;
        col.add_note(&mut note, DeckId(1))?;
        let card_id = col.answer_easy().card_id;
        col.set_due_date(&[card_id], "0", None)?;
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "rules": [{"query": "deck:Default", "desired_retention": 0.85}],
                "grade_rules": [{"grade": "good", "mode": "absolute", "value": 0.85}]
            }),
        )?;
        let choices = col.get_scheduling_states(card_id)?;
        let recorded_target = choices.dynamic_desired_retentions.unwrap()[2];
        assert_ne!((recorded_target * 100.0).round() / 100.0, 0.85);
        let CardState::Normal(NormalState::Review(good)) = choices.good else {
            panic!("expected a review state");
        };
        let stability = good.memory_state.unwrap().stability_internal;
        let interval = col.fsrs_interval_at_retrievability_for_card(card_id, stability, 0.85)?;
        assert_eq!(good.scheduled_days, ((interval + 0.5) as u32).max(1));
        col.answer_good();
        assert_eq!(col.get_first_card().desired_retention, Some(0.85));

        // No grade rule matches Easy, so native Dynamic DR still selects and
        // saves its per-grade mapped retention.
        col.undo()?;
        let choices = col.get_scheduling_states(card_id)?;
        let mapped_easy = choices.dynamic_desired_retentions.unwrap()[3];
        col.answer_easy();
        assert_eq!(
            col.get_first_card().desired_retention,
            Some((mapped_easy * 100.0).round() / 100.0)
        );

        // A caller can supply a different scheduling state. Do not pretend
        // the grade target was applied to that modified interval.
        col.undo()?;
        let choices = col.get_scheduling_states(card_id)?;
        let mapped_good = choices.dynamic_desired_retentions.unwrap()[2];
        let mut modified_good = choices.good;
        let CardState::Normal(NormalState::Review(ref mut review)) = modified_good else {
            panic!("expected a review state");
        };
        review.scheduled_days += 1;
        col.answer_card(&mut CardAnswer {
            card_id,
            current_state: choices.current,
            new_state: modified_good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: false,
        })?;
        assert_eq!(
            col.get_first_card().desired_retention,
            Some((mapped_good * 100.0).round() / 100.0)
        );

        // Interval bounds are applied after the grade target; they must not
        // make a valid bounded target look like an externally modified state.
        col.undo()?;
        let note_id = col.get_first_card().note_id;
        let mut note = col.storage.get_note(note_id)?.unwrap();
        note.set_field(0, "10")?;
        col.update_note(&mut note)?;
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "field_rules": [{"note_type": "Basic", "field": "Front", "value_type": "number",
                    "thresholds": [{"value": "10", "desired_retention": 0.85,
                        "minimum_interval": 15, "maximum_interval": 15}]}],
                "grade_rules": [{"grade": "good", "mode": "absolute", "value": 0.85}]
            }),
        )?;
        let choices = col.get_scheduling_states(card_id)?;
        let CardState::Normal(NormalState::Review(good)) = choices.good else {
            panic!("expected a review state");
        };
        assert_eq!(good.scheduled_days, 15);
        col.answer_good();
        assert_eq!(col.get_first_card().desired_retention, Some(0.85));
        Ok(())
    }

    #[test]
    fn unsupported_grade_state_keeps_native_dynamic_retention() -> Result<()> {
        use crate::config::BoolKey;
        use crate::scheduler::states::CardState;
        use crate::scheduler::states::NormalState;

        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_dynamic_desired_retention_enabled = true;
            config.fsrs_dynamic_desired_retention_params = vec![0.0; 15];
            config.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
            config.fsrs_dynamic_desired_retention_avg_drs = vec![0.8, 0.9];
            config.fsrs_dynamic_desired_retention_min = 0.75;
            config.fsrs_dynamic_desired_retention_max = 0.95;
        });
        let notetype = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = notetype.new_note();
        note.set_field(0, "new card")?;
        col.add_note(&mut note, DeckId(1))?;
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "rules": [{"query": "deck:Default", "desired_retention": 0.85}],
                "grade_rules": [{"grade": "again", "mode": "absolute", "value": 0.85}]
            }),
        )?;
        let card_id = col.get_first_card().id;
        let states = col.get_scheduling_states(card_id)?;
        assert!(matches!(
            states.again,
            CardState::Normal(NormalState::Learning(_))
        ));
        let mapped = states.dynamic_desired_retentions.unwrap()[0];
        col.answer_again();
        assert_eq!(
            col.get_first_card().desired_retention,
            Some((mapped * 100.0).round() / 100.0)
        );
        Ok(())
    }

    #[test]
    fn relative_grade_uses_an_explicit_base_override_on_answer() -> Result<()> {
        use crate::config::BoolKey;
        use crate::scheduler::answering::CardAnswer;
        use crate::scheduler::answering::Rating;

        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        let notetype = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = notetype.new_note();
        note.set_field(0, "example")?;
        col.add_note(&mut note, DeckId(1))?;
        let card_id = col.answer_easy().card_id;
        col.set_due_date(&[card_id], "0", None)?;
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "grade_rules": [{"grade": "good", "mode": "relative", "value": 0.5}]
            }),
        )?;
        let states =
            col.get_scheduling_states_with_desired_retention_override(card_id, Some(0.6))?;
        col.answer_card(&mut CardAnswer {
            card_id,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: Some(0.6),
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: false,
        })?;
        assert_eq!(col.get_first_card().desired_retention, Some(0.8));

        // A caller that has already supplied a final target must not have the
        // relative grade rule applied to it a second time.
        col.undo()?;
        col.set_config(CONFIG_KEY, &json!({"version": 1, "grade_rules": []}))?;
        let states =
            col.get_scheduling_states_with_desired_retention_override(card_id, Some(0.6))?;
        col.set_config(
            CONFIG_KEY,
            &json!({
                "version": 1,
                "grade_rules": [{"grade": "good", "mode": "relative", "value": 0.5}]
            }),
        )?;
        col.answer_card(&mut CardAnswer {
            card_id,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: Some(0.6),
            desired_retention_override_is_final: true,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: false,
        })?;
        assert_eq!(col.get_first_card().desired_retention, Some(0.6));
        Ok(())
    }
}
