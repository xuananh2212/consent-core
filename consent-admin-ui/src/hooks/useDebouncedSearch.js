import { useEffect, useState } from "react";

const DEFAULT_DELAY = 500;

export function useDebouncedValue(value, delay = DEFAULT_DELAY) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}

export function useDebouncedSearch(source = "", delay = DEFAULT_DELAY) {
  const [keyword, setKeyword] = useState(source);
  const debouncedKeyword = useDebouncedValue(keyword, delay);

  useEffect(() => {
    setKeyword((current) => (current === source ? current : source));
  }, [source]);

  return { keyword, setKeyword, debouncedKeyword };
}
