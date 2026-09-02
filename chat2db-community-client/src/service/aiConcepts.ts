import createRequest from './base';

/**
 * One agreed figure, defined once for the whole installation.
 *
 * The expression names no tables: it refers to labels like `{sales}`, and what
 * each label points at belongs to the connection. That is what lets a single
 * definition be used at every customer built to the same shape.
 */
export interface IAiMetric {
  id: string;
  name: string;
  aliases?: string[];
  description?: string;
  grain?: string;
  requires?: string[];
  sql?: string;
  filter?: string;
  timeColumn?: string;
  enabled?: boolean;
}

export interface IAiGlossaryEntry {
  term: string;
  meaning?: string;
}

export interface IAiConceptLibrary {
  version: number;
  notes?: string;
  conventions?: Record<string, string>;
  metrics: IAiMetric[];
  glossary?: IAiGlossaryEntry[];
}

/** One label, and what this connection appears to offer for it. */
export interface IBindingSuggestion {
  source: string;
  /** Filled in only when the platform is confident; otherwise choose from candidates. */
  suggested?: string | null;
  candidates: string[];
}

export interface IBindingsView {
  libraryVersion: number;
  bindings: Record<string, string>;
  suggestions: IBindingSuggestion[];
}

export interface IMetricTestResult {
  succeeded: boolean;
  sql?: string;
  message?: string;
  missingSources?: string[];
}

const readLibrary = createRequest<void, IAiConceptLibrary>('/api/ai/concepts', { errorLevel: false });

const writeLibrary = createRequest<IAiConceptLibrary, IAiConceptLibrary>('/api/ai/concepts', {
  method: 'put',
});

const readBindings = createRequest<
  { dataSourceId: number; databaseName?: string; schemaName?: string },
  IBindingsView
>('/api/ai/concepts/bindings', { errorLevel: false });

const writeBindings = createRequest<{ dataSourceId: number; bindings: Record<string, string> }, void>(
  '/api/ai/concepts/bindings',
  { method: 'put' },
);

const testMetric = createRequest<
  {
    metricId: string;
    dataSourceId: number;
    databaseName?: string;
    schemaName?: string;
    bindings: Record<string, string>;
  },
  IMetricTestResult
>('/api/ai/concepts/test', { method: 'post', errorLevel: false });

export default {
  readLibrary,
  writeLibrary,
  readBindings,
  writeBindings,
  testMetric,
};
