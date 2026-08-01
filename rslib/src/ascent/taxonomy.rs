// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! The official AAMC content outline, transcribed from
//! `mcat-content-outline.json` (compiled 2026-07-31 from AAMC primary
//! sources). Foundational-concept weights are published; **content-category
//! weights are not**, and none is stored here. See report.md §1.4 — inventing
//! one is the failure mode this table exists to make impossible.
//!
//! CARS has neither foundational concepts nor content categories and is
//! therefore absent by construction, not by omission.

#[derive(Debug, PartialEq, Eq)]
pub(crate) struct FoundationalConcept {
    pub id: &'static str,
    pub section: &'static str,
    pub weight_pct: u32,
    pub title: &'static str,
}

#[derive(Debug, PartialEq, Eq)]
pub(crate) struct ContentCategory {
    pub id: &'static str,
    /// The foundational concept this category sits under.
    pub concept: &'static str,
    pub section: &'static str,
    pub title: &'static str,
}
pub(crate) const FOUNDATIONAL_CONCEPTS: &[FoundationalConcept] = &[
    FoundationalConcept { id: "4", section: "C/P", weight_pct: 40, title: "Complex living organisms transport materials, sense their environment, process signals, and respond to changes using processes understood in terms of physical principles." },
    FoundationalConcept { id: "5", section: "C/P", weight_pct: 60, title: "The principles that govern chemical interactions and reactions form the basis for a broader understanding of the molecular dynamics of living systems." },
    FoundationalConcept { id: "1", section: "B/B", weight_pct: 55, title: "Biomolecules have unique properties that determine how they contribute to the structure and function of cells and how they participate in the processes necessary to maintain life." },
    FoundationalConcept { id: "2", section: "B/B", weight_pct: 20, title: "Highly organized assemblies of molecules, cells, and organs interact to carry out the functions of living organisms." },
    FoundationalConcept { id: "3", section: "B/B", weight_pct: 25, title: "Complex systems of tissues and organs sense the internal and external environments of multicellular organisms, and through integrated functioning, maintain a stable internal environment within an ever-changing external environment." },
    FoundationalConcept { id: "6", section: "P/S", weight_pct: 25, title: "Biological, psychological, and sociocultural factors influence the ways that individuals perceive, think about, and react to the world." },
    FoundationalConcept { id: "7", section: "P/S", weight_pct: 35, title: "Biological, psychological, and sociocultural factors influence behavior and behavior change." },
    FoundationalConcept { id: "8", section: "P/S", weight_pct: 20, title: "Psychological, sociocultural, and biological factors influence the way we think about ourselves and others, as well as how we interact with others." },
    FoundationalConcept { id: "9", section: "P/S", weight_pct: 15, title: "Cultural and social differences influence well-being." },
    FoundationalConcept { id: "10", section: "P/S", weight_pct: 5, title: "Social stratification and access to resources influence well-being." },
];

pub(crate) const CONTENT_CATEGORIES: &[ContentCategory] = &[
    ContentCategory { id: "4A", concept: "4", section: "C/P", title: "Translational motion, forces, work, energy, and equilibrium in living systems." },
    ContentCategory { id: "4B", concept: "4", section: "C/P", title: "Importance of fluids for the circulation of blood, gas movement, and gas exchange." },
    ContentCategory { id: "4C", concept: "4", section: "C/P", title: "Electrochemistry and electrical circuits and their elements." },
    ContentCategory { id: "4D", concept: "4", section: "C/P", title: "How light and sound interact with matter." },
    ContentCategory { id: "4E", concept: "4", section: "C/P", title: "Atoms, nuclear decay, electronic structure, and atomic chemical behavior." },
    ContentCategory { id: "5A", concept: "5", section: "C/P", title: "Unique nature of water and its solutions." },
    ContentCategory { id: "5B", concept: "5", section: "C/P", title: "Nature of molecules and intermolecular interactions." },
    ContentCategory { id: "5C", concept: "5", section: "C/P", title: "Separation and purification methods." },
    ContentCategory { id: "5D", concept: "5", section: "C/P", title: "Structure, function, and reactivity of biologically relevant molecules." },
    ContentCategory { id: "5E", concept: "5", section: "C/P", title: "Principles of chemical thermodynamics and kinetics." },
    ContentCategory { id: "1A", concept: "1", section: "B/B", title: "Structure and function of proteins and their constituent amino acids." },
    ContentCategory { id: "1B", concept: "1", section: "B/B", title: "Transmission of genetic information from the gene to the protein." },
    ContentCategory { id: "1C", concept: "1", section: "B/B", title: "Transmission of heritable information from generation to generation and the processes that increase genetic diversity." },
    ContentCategory { id: "1D", concept: "1", section: "B/B", title: "Principles of bioenergetics and fuel molecule metabolism." },
    ContentCategory { id: "2A", concept: "2", section: "B/B", title: "Assemblies of molecules, cells, and groups of cells within single cellular and multicellular organisms." },
    ContentCategory { id: "2B", concept: "2", section: "B/B", title: "The structure, growth, physiology, and genetics of prokaryotes and viruses." },
    ContentCategory { id: "2C", concept: "2", section: "B/B", title: "Processes of cell division, differentiation, and specialization." },
    ContentCategory { id: "3A", concept: "3", section: "B/B", title: "Structure and functions of the nervous and endocrine systems and ways these systems coordinate the organ systems." },
    ContentCategory { id: "3B", concept: "3", section: "B/B", title: "Structure and integrative functions of the main organ systems." },
    ContentCategory { id: "6A", concept: "6", section: "P/S", title: "Sensing the environment" },
    ContentCategory { id: "6B", concept: "6", section: "P/S", title: "Making sense of the environment" },
    ContentCategory { id: "6C", concept: "6", section: "P/S", title: "Responding to the world" },
    ContentCategory { id: "7A", concept: "7", section: "P/S", title: "Individual influences on behavior" },
    ContentCategory { id: "7B", concept: "7", section: "P/S", title: "Social processes that influence human behavior" },
    ContentCategory { id: "7C", concept: "7", section: "P/S", title: "Attitude and behavior change" },
    ContentCategory { id: "8A", concept: "8", section: "P/S", title: "Self-identity" },
    ContentCategory { id: "8B", concept: "8", section: "P/S", title: "Social thinking" },
    ContentCategory { id: "8C", concept: "8", section: "P/S", title: "Social interactions" },
    ContentCategory { id: "9A", concept: "9", section: "P/S", title: "Understanding social structure" },
    ContentCategory { id: "9B", concept: "9", section: "P/S", title: "Demographic characteristics and processes" },
    ContentCategory { id: "10A", concept: "10", section: "P/S", title: "Social inequality" },
];
