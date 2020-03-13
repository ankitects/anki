import React from "react";

interface BrowseSearchInputProps {
  onSearchChanged: (txt: string) => void;
}

export const BrowseSearchInput = ({
  onSearchChanged
}: BrowseSearchInputProps) => {
  let currentInput = "";

  function onInput(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onSearchChanged(currentInput);
  }

  function onInputChange(e: React.FormEvent<HTMLInputElement>) {
    currentInput = (e.target as any).value;
  }

  return (
    <form onSubmit={onInput}>
      <input
        onChange={onInputChange}
        autoFocus
        placeholder="Hit enter to search"
      />
    </form>
  );
};
