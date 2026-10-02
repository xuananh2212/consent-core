import { STATUS_LABEL } from "./constants.js";

export function formatWhen(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

export function statusLabel(status) {
  return STATUS_LABEL[status] || status;
}
