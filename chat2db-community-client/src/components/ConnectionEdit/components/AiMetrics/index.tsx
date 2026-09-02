import { useEffect, useState } from 'react';
import { AutoComplete, Button, Tag } from 'antd';
import { CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { staticMessage } from '@chat2db/ui';
import aiConceptsService, { IAiConceptLibrary, IBindingSuggestion } from '@/service/aiConcepts';
import { i18n } from '@/i18n';
import styles from './index.less';

/**
 * Which view here implements which figure in the shared library.
 *
 * The definitions themselves are not edited on this screen and deliberately so.
 * "Monthly sales" means one thing across every customer, so it is written once
 * in the library; what differs from site to site - and the only thing that does
 * - is which physical view carries the data. Editing the definition here would
 * turn one standard into forty copies of it.
 *
 * Every row is a proposal until somebody confirms it. The platform matches the
 * connection's view names against the library's labels and fills in what it is
 * confident about, which on a warehouse built by the same ETL is nearly all of
 * them - but a wrong binding produces a plausible figure from the wrong table,
 * and the only defence against that is that a person looked and pressed Test.
 */

interface IProps {
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  value?: Record<string, string> | null;
  onChange: (bindings: Record<string, string>) => void;
  disabled?: boolean;
}

const AiMetrics = (props: IProps) => {
  const { dataSourceId, databaseName, schemaName, value, onChange, disabled } = props;
  const [library, setLibrary] = useState<IAiConceptLibrary | null>(null);
  const [suggestions, setSuggestions] = useState<IBindingSuggestion[]>([]);
  const [testing, setTesting] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const bindings = value || {};

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      aiConceptsService.readLibrary(),
      dataSourceId
        ? aiConceptsService.readBindings({ dataSourceId, databaseName, schemaName })
        : Promise.resolve(null),
    ])
      .then(([loadedLibrary, view]) => {
        if (cancelled) {
          return;
        }
        setLibrary(loadedLibrary || null);
        setSuggestions(view?.suggestions || []);
        // Fill in what the platform is confident about, but never over an
        // answer somebody already gave.
        const proposed: Record<string, string> = {};
        (view?.suggestions || []).forEach((suggestion) => {
          if (suggestion.suggested && !bindings[suggestion.source]) {
            proposed[suggestion.source] = suggestion.suggested;
          }
        });
        if (Object.keys(proposed).length) {
          onChange({ ...bindings, ...proposed });
        }
      })
      .catch(() => undefined)
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dataSourceId, databaseName, schemaName]);

  const runTest = async (metricId: string) => {
    if (!dataSourceId) {
      staticMessage.warning(i18n('connection.aiMetrics.saveFirst'));
      return;
    }
    setTesting(metricId);
    try {
      const result = await aiConceptsService.testMetric({
        metricId,
        dataSourceId,
        databaseName,
        schemaName,
        bindings,
      });
      if (result?.succeeded) {
        staticMessage.success(i18n('connection.aiMetrics.testPassed', result.sql || ''));
      } else {
        staticMessage.error(result?.message || i18n('connection.aiMetrics.testFailed'));
      }
    } catch (error: any) {
      staticMessage.error(error?.message || i18n('connection.aiMetrics.testFailed'));
    } finally {
      setTesting(null);
    }
  };

  const metrics = (library?.metrics || []).filter((metric) => metric.enabled !== false);

  if (!loading && !metrics.length) {
    return (
      <div className={styles.box}>
        <p className={styles.intro}>{i18n('connection.aiMetrics.empty')}</p>
      </div>
    );
  }

  return (
    <div className={styles.box}>
      <p className={styles.intro}>
        {i18n('connection.aiMetrics.intro', String(library?.version ?? 1))}
      </p>

      <div className={styles.sources}>
        {suggestions.map((suggestion) => {
          const bound = bindings[suggestion.source];
          return (
            <div key={suggestion.source} className={styles.source}>
              <code className={styles.label}>{`{${suggestion.source}}`}</code>
              <AutoComplete
                className={styles.picker}
                value={bound || ''}
                disabled={disabled}
                options={suggestion.candidates.map((candidate) => ({ value: candidate }))}
                placeholder={i18n('connection.aiMetrics.choose')}
                onChange={(next) => onChange({ ...bindings, [suggestion.source]: next })}
                allowClear
              />
              {bound ? (
                <Tag color="success" icon={<CheckCircleOutlined />} className={styles.state}>
                  {i18n('connection.aiMetrics.bound')}
                </Tag>
              ) : (
                <Tag color="warning" icon={<ExclamationCircleOutlined />} className={styles.state}>
                  {i18n('connection.aiMetrics.unbound')}
                </Tag>
              )}
            </div>
          );
        })}
      </div>

      <div className={styles.metrics}>
        {metrics.map((metric) => {
          const missing = (metric.requires || []).filter((source) => !bindings[source]);
          return (
            <div key={metric.id} className={styles.metric}>
              <div className={styles.metricHead}>
                <span className={styles.metricName}>{metric.name || metric.id}</span>
                <Button
                  size="small"
                  disabled={disabled || missing.length > 0}
                  loading={testing === metric.id}
                  onClick={() => runTest(metric.id)}
                >
                  {i18n('connection.aiMetrics.test')}
                </Button>
              </div>
              {metric.description && <div className={styles.metricNote}>{metric.description}</div>}
              {missing.length > 0 && (
                <div className={styles.metricMissing}>
                  {i18n('connection.aiMetrics.needs', missing.map((s) => `{${s}}`).join('، '))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default AiMetrics;
