export function formatRupees(amount: number): string {
  return `₹${amount.toFixed(2)}`;
}

/** 2 -> "2", 2.5 -> "2.5" (no trailing zeros). */
export function formatQty(qty: number): string {
  return String(Math.round(qty * 1000) / 1000);
}
