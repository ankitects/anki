// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// CFA Speedrun: curated Reading -> CFA Level I topic map, used only to lay the
// concept graph out (readings from the same topic are pulled into their own
// region). This is frontend-only and purely cosmetic; the scheduler/engine
// stays topic-agnostic. Tags that aren't in the table fall back to "Other".

const TOPIC_BY_READING: Record<string, string> = {
    Ethics_and_Trust_in_the_Investment_Profession: "Ethical & Professional Standards",

    The_Time_Value_of_Money: "Quantitative Methods",
    Statistical_Concepts_and_Market_Returns: "Quantitative Methods",
    Probability_Concepts: "Quantitative Methods",
    Common_Probability_Distributions: "Quantitative Methods",
    Sampling_and_Estimation: "Quantitative Methods",
    Hypothesis_Testing: "Quantitative Methods",
    Technical_Analysis: "Quantitative Methods",

    Topics_in_Supply_and_Demand_Analysis: "Economics",
    The_Firm_and_Market_Structures: "Economics",
    "Aggregate_Output,_Prices,_and_Economic_Growth": "Economics",
    Understanding_Business_Cycles: "Economics",
    Monetary_and_Fiscal_Policy: "Economics",
    International_Trade_and_Capital_Flows: "Economics",
    Currency_Exchange_Rates: "Economics",

    Introduction_to_Financial_Statement_Analysis: "Financial Statement Analysis",
    Financial_Reporting_Standards: "Financial Statement Analysis",
    Understanding_Income_Statements: "Financial Statement Analysis",
    Understanding_Balance_Sheets: "Financial Statement Analysis",
    Understanding_Cash_Flow_Statements: "Financial Statement Analysis",
    Financial_Analysis_Techniques: "Financial Statement Analysis",
    Inventories: "Financial Statement Analysis",
    "Long-Lived_Assets": "Financial Statement Analysis",
    Income_Taxes: "Financial Statement Analysis",
    "Non-Current_(Long-Term)_Liabilities": "Financial Statement Analysis",
    Applications_of_Financial_Statement_Analysis: "Financial Statement Analysis",

    Introduction_to_Corporate_Governance_and_Other_ESG_Considerations: "Corporate Issuers",
    Capital_Budgeting: "Corporate Issuers",
    Cost_of_Capital: "Corporate Issuers",
    Measures_of_Leverage: "Corporate Issuers",
    Working_Capital_Management: "Corporate Issuers",

    Market_Organization_and_Structure: "Equity Investments",
    Security_Market_Indexes: "Equity Investments",
    Market_Efficiency: "Equity Investments",
    Overview_of_Equity_Securities: "Equity Investments",
    Introduction_to_Industry_and_Company_Analysis: "Equity Investments",
    "Equity_Valuation:_Concepts_and_Basic_Tools": "Equity Investments",

    "Fixed-Income_Securities:_Defining_Elements": "Fixed Income",
    "Fixed-Income_Markets:_Issuance,_Trading,_and_Funding": "Fixed Income",
    "Introduction_to_Fixed-Income_Valuation": "Fixed Income",
    "Introduction_to_Asset-Backed_Securities": "Fixed Income",
    "Understanding_Fixed-Income_Risk_and_Return": "Fixed Income",
    Fundamentals_of_Credit_Analysis: "Fixed Income",

    Derivative_Markets_and_Instruments: "Derivatives",
    Basics_of_Derivative_Pricing_and_Valuation: "Derivatives",

    Introduction_to_Alternative_Investments: "Alternative Investments",

    "Portfolio_Management:_An_Overview": "Portfolio Management",
    "Portfolio_Risk_and_Return:_Part_I": "Portfolio Management",
    "Portfolio_Risk_and_Return:_Part_II": "Portfolio Management",
    Basics_of_Portfolio_Planning_and_Construction: "Portfolio Management",
    Introduction_to_Risk_Management: "Portfolio Management",
    Fintech_in_Investment_Management: "Portfolio Management",
};

/** The CFA topic a reading tag belongs to, or "Other" when unknown. */
export function topicOf(reading: string): string {
    return TOPIC_BY_READING[reading] ?? "Other";
}
