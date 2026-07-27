import assert from 'node:assert/strict';
import { reconcileSelectedModel } from './modelSelectOptions';

const gpt = { label: 'GPT-4o', value: 'gpt-4o' };
const claude = { label: 'Claude', value: 'claude', defaultOption: true };

// Nothing stored yet: adopt the option flagged as default, not merely the first.
assert.deepEqual(reconcileSelectedModel(null, [gpt, claude]), { value: 'claude', label: 'Claude' });

// Nothing flagged: fall back to the first option.
assert.deepEqual(reconcileSelectedModel(null, [gpt]), { value: 'gpt-4o', label: 'GPT-4o' });

// Still there and unchanged: leave the store alone.
assert.equal(reconcileSelectedModel({ value: 'gpt-4o', label: 'GPT-4o' }, [gpt]), undefined);

// Same model, renamed in the config dialog: refresh the label.
assert.deepEqual(reconcileSelectedModel({ value: 'gpt-4o', label: 'Old name' }, [gpt]), {
  value: 'gpt-4o',
  label: 'GPT-4o',
});

// Selected model deleted while others remain: move to the default.
assert.deepEqual(reconcileSelectedModel({ value: 'gone', label: 'Gone' }, [gpt, claude]), {
  value: 'claude',
  label: 'Claude',
});

// Last model deleted: clear, so the picker stops offering an unusable choice.
assert.equal(reconcileSelectedModel({ value: 'gone', label: 'Gone' }, []), null);

// Empty list and nothing stored: no write, so the store is not churned on every load.
assert.equal(reconcileSelectedModel(null, []), undefined);

console.log('AI model select option tests passed.');
