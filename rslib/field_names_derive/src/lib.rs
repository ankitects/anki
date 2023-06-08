// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Derive macro for the trait [anki::serde::FieldNames].

use proc_macro::TokenStream;
use quote::quote;
use syn::parse_macro_input;
use syn::ItemStruct;

/// Derive macro for the trait [anki::serde::FieldNames].
#[proc_macro_derive(FieldNames)]
pub fn derive_field_names(input: TokenStream) -> TokenStream {
    let item = parse_macro_input!(input as ItemStruct);
    let name = &item.ident;
    let field_names = item
        .fields
        .iter()
        .filter_map(|f| f.ident.as_ref())
        .map(ToString::to_string);

    quote! {
        impl crate::serde::FieldNames for #name {
            /// The named fields of this struct as strings.
            fn field_names() -> &'static [&'static str] {
                &[#(#field_names),*]
            }
        }
    }
    .into()
}
