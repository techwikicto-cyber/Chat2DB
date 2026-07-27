import type { ReactNode } from 'react';
import type { SelectedModelOption } from '@/store/ai/slices/model/initialState';

export interface ModelSelectOption {
  label: ReactNode;
  value: string;
  isDefault?: boolean;
}

/** Shape returned by `listAvailableModelOptions`, narrowed to what reconciling needs. */
export interface AvailableModelOption {
  label: string;
  value: string;
  defaultOption?: boolean;
}

/**
 * Reconcile the stored model selection against the list the server just returned.
 *
 * Returns the selection to store, or `undefined` when the stored one is already
 * correct and should be left alone.
 *
 * Clearing matters as much as picking: the picker draws whatever label the store
 * holds, whether or not a row still backs it, so after the last configured model
 * is deleted the stale name would keep showing as a choice that can no longer be
 * sent.
 */
export const reconcileSelectedModel = (
  selected: SelectedModelOption | null | undefined,
  options: readonly AvailableModelOption[],
): SelectedModelOption | null | undefined => {
  const current = selected?.value ? options.find((option) => option.value === selected.value) : undefined;

  if (current) {
    // Same model, renamed upstream - refresh the label, keep the selection.
    return current.label === selected?.label ? undefined : { value: current.value, label: current.label };
  }

  if (options.length === 0) {
    return selected ? null : undefined;
  }

  const fallback = options.find((option) => option.defaultOption) || options[0];
  return { value: fallback.value, label: fallback.label };
};
