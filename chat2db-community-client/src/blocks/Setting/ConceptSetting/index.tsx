import { useEffect, useRef, useState } from 'react';
import { Button, Collapse, Empty, Input, InputNumber, Space, Switch, Tag } from 'antd';
import { DeleteOutlined, DownloadOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { staticMessage } from '@chat2db/ui';
import SettingSubsection from '../SettingSubsection';
import aiConceptsService, {
  IAiConceptLibrary,
  IAiGlossaryEntry,
  IAiMetric,
} from '@/service/aiConcepts';
import i18n from '@/i18n';
import styles from './index.less';

/**
 * Where the organisation writes down what its figures mean.
 *
 * One library for the installation, edited here and nowhere else. The
 * connection screens say which view carries the data; this says what the data
 * is, and it says it once - a team whose work is standardising what "monthly
 * sales" means across forty customers cannot keep forty answers to it.
 *
 * The version at the top is not decoration. It travels with every figure
 * computed from this library, so when a number from last month disagrees with
 * the same number today, it is what turns "the numbers are wrong" into "the
 * definition changed in April". Bumping it is the author's job, because only
 * they know whether an edit was a correction or a change of meaning.
 */

const emptyLibrary: IAiConceptLibrary = { version: 1, metrics: [], conventions: {}, glossary: [] };

const ConceptSetting = () => {
  const [library, setLibrary] = useState<IAiConceptLibrary>(emptyLibrary);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    aiConceptsService
      .readLibrary()
      .then((loaded) => setLibrary(loaded || emptyLibrary))
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, []);

  const patchMetric = (index: number, patch: Partial<IAiMetric>) => {
    const metrics = [...library.metrics];
    metrics[index] = { ...metrics[index], ...patch };
    setLibrary({ ...library, metrics });
  };

  const save = async () => {
    setSaving(true);
    try {
      const stored = await aiConceptsService.writeLibrary(library);
      setLibrary(stored || library);
      staticMessage.success(i18n('setting.concepts.saved', String(library.version)));
    } catch (error: any) {
      // The server refuses a library whole rather than in part: a half-saved
      // one is where some figures moved to the new definition and some did not.
      staticMessage.error(error?.message || i18n('setting.concepts.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const exportLibrary = () => {
    const file = new Blob([JSON.stringify(library, null, 2)], { type: 'application/json' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(file);
    link.download = `concepts-v${library.version}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
  };

  const importLibrary = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result));
        // Loaded into the editor, not saved. Whoever imports a library from
        // another installation should see what they are about to adopt before
        // it becomes what every answer here is computed from.
        setLibrary({ ...emptyLibrary, ...parsed });
        staticMessage.success(i18n('setting.concepts.imported', String(parsed?.metrics?.length ?? 0)));
      } catch {
        staticMessage.error(i18n('setting.concepts.importFailed'));
      }
    };
    reader.readAsText(file);
  };

  const conventionRows = Object.entries(library.conventions || {});

  return (
    <div className={styles.page}>
      <SettingSubsection
        title={i18n('setting.concepts.title')}
        describe={i18n('setting.concepts.describe')}
      />

      <div className={styles.toolbar}>
        <Space>
          <span className={styles.versionLabel}>{i18n('setting.concepts.version')}</span>
          <InputNumber
            min={1}
            value={library.version}
            onChange={(next) => setLibrary({ ...library, version: Number(next) || 1 })}
            className={styles.version}
          />
        </Space>
        <Space>
          <Button icon={<UploadOutlined />} onClick={() => fileInput.current?.click()}>
            {i18n('setting.concepts.import')}
          </Button>
          <Button icon={<DownloadOutlined />} onClick={exportLibrary}>
            {i18n('setting.concepts.export')}
          </Button>
          <Button type="primary" loading={saving} onClick={save}>
            {i18n('common.button.save')}
          </Button>
        </Space>
      </div>

      <Input.TextArea
        value={library.notes || ''}
        onChange={(event) => setLibrary({ ...library, notes: event.target.value })}
        placeholder={i18n('setting.concepts.notesPlaceholder')}
        autoSize={{ minRows: 2, maxRows: 4 }}
        className={styles.notes}
      />

      {/* ── metrics ─────────────────────────────────────────────── */}
      <div className={styles.sectionHead}>
        <h3 className={styles.sectionTitle}>{i18n('setting.concepts.metrics')}</h3>
        <Button
          size="small"
          icon={<PlusOutlined />}
          onClick={() =>
            setLibrary({
              ...library,
              metrics: [...library.metrics, { id: '', name: '', requires: [], sql: '', enabled: true }],
            })
          }
        >
          {i18n('setting.concepts.addMetric')}
        </Button>
      </div>

      {!loading && !library.metrics.length && (
        <Empty description={i18n('setting.concepts.noMetrics')} className={styles.empty} />
      )}

      <Collapse
        className={styles.metrics}
        items={library.metrics.map((metric, index) => ({
          key: String(index),
          label: (
            <div className={styles.metricLabel}>
              <span>{metric.name || metric.id || i18n('setting.concepts.untitledMetric')}</span>
              {metric.enabled === false && <Tag>{i18n('setting.concepts.retired')}</Tag>}
            </div>
          ),
          extra: (
            <DeleteOutlined
              onClick={(event) => {
                event.stopPropagation();
                setLibrary({ ...library, metrics: library.metrics.filter((_, i) => i !== index) });
              }}
            />
          ),
          children: (
            <div className={styles.metricForm}>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.id')}</span>
                <Input
                  value={metric.id}
                  onChange={(e) => patchMetric(index, { id: e.target.value })}
                  placeholder="monthly_sales"
                  className={styles.ltr}
                />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.name')}</span>
                <Input value={metric.name} onChange={(e) => patchMetric(index, { name: e.target.value })} />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.aliases')}</span>
                <Input
                  value={(metric.aliases || []).join('، ')}
                  onChange={(e) =>
                    patchMetric(index, {
                      aliases: e.target.value
                        .split(/[،,]/)
                        .map((a) => a.trim())
                        .filter(Boolean),
                    })
                  }
                  placeholder={i18n('setting.concepts.field.aliasesPlaceholder')}
                />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.description')}</span>
                <Input.TextArea
                  value={metric.description || ''}
                  onChange={(e) => patchMetric(index, { description: e.target.value })}
                  autoSize={{ minRows: 2, maxRows: 4 }}
                />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.grain')}</span>
                <Input
                  value={metric.grain || ''}
                  onChange={(e) => patchMetric(index, { grain: e.target.value })}
                  placeholder={i18n('setting.concepts.field.grainPlaceholder')}
                />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.requires')}</span>
                <Input
                  value={(metric.requires || []).join(', ')}
                  onChange={(e) =>
                    patchMetric(index, {
                      requires: e.target.value
                        .split(/[،,]/)
                        .map((r) => r.trim())
                        .filter(Boolean),
                    })
                  }
                  placeholder="sales, customer"
                  className={styles.ltr}
                />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.sql')}</span>
                <Input.TextArea
                  value={metric.sql || ''}
                  onChange={(e) => patchMetric(index, { sql: e.target.value })}
                  placeholder="SUM({sales}.NetAmount)"
                  autoSize={{ minRows: 2, maxRows: 4 }}
                  className={styles.ltr}
                />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.filter')}</span>
                <Input.TextArea
                  value={metric.filter || ''}
                  onChange={(e) => patchMetric(index, { filter: e.target.value })}
                  placeholder="{sales}.DocType = 'INV' AND {sales}.IsVoid = 0"
                  autoSize={{ minRows: 2, maxRows: 3 }}
                  className={styles.ltr}
                />
              </label>
              <label className={styles.field}>
                <span>{i18n('setting.concepts.field.timeColumn')}</span>
                <Input
                  value={metric.timeColumn || ''}
                  onChange={(e) => patchMetric(index, { timeColumn: e.target.value })}
                  placeholder="{sales}.DocDate"
                  className={styles.ltr}
                />
              </label>
              <label className={styles.fieldInline}>
                <Switch
                  checked={metric.enabled !== false}
                  onChange={(checked) => patchMetric(index, { enabled: checked })}
                />
                <span>{i18n('setting.concepts.field.enabled')}</span>
              </label>
            </div>
          ),
        }))}
      />

      {/* ── conventions ─────────────────────────────────────────── */}
      <div className={styles.sectionHead}>
        <h3 className={styles.sectionTitle}>{i18n('setting.concepts.conventions')}</h3>
        <Button
          size="small"
          icon={<PlusOutlined />}
          onClick={() =>
            setLibrary({ ...library, conventions: { ...(library.conventions || {}), '': '' } })
          }
        >
          {i18n('setting.concepts.addRow')}
        </Button>
      </div>
      <p className={styles.sectionNote}>{i18n('setting.concepts.conventionsNote')}</p>
      <div className={styles.rows}>
        {conventionRows.map(([key, value], index) => (
          <div key={index} className={styles.row}>
            <Input
              value={key}
              placeholder="calendar"
              className={styles.ltr}
              onChange={(event) => {
                const next: Record<string, string> = {};
                conventionRows.forEach(([k, v], i) => {
                  next[i === index ? event.target.value : k] = v;
                });
                setLibrary({ ...library, conventions: next });
              }}
            />
            <Input
              value={value}
              placeholder="jalali"
              onChange={(event) =>
                setLibrary({
                  ...library,
                  conventions: { ...(library.conventions || {}), [key]: event.target.value },
                })
              }
            />
            <Button
              type="text"
              icon={<DeleteOutlined />}
              onClick={() => {
                const next = { ...(library.conventions || {}) };
                delete next[key];
                setLibrary({ ...library, conventions: next });
              }}
            />
          </div>
        ))}
      </div>

      {/* ── glossary ────────────────────────────────────────────── */}
      <div className={styles.sectionHead}>
        <h3 className={styles.sectionTitle}>{i18n('setting.concepts.glossary')}</h3>
        <Button
          size="small"
          icon={<PlusOutlined />}
          onClick={() =>
            setLibrary({ ...library, glossary: [...(library.glossary || []), { term: '', meaning: '' }] })
          }
        >
          {i18n('setting.concepts.addRow')}
        </Button>
      </div>
      <p className={styles.sectionNote}>{i18n('setting.concepts.glossaryNote')}</p>
      <div className={styles.rows}>
        {(library.glossary || []).map((entry: IAiGlossaryEntry, index) => (
          <div key={index} className={styles.row}>
            <Input
              value={entry.term}
              placeholder={i18n('setting.concepts.termPlaceholder')}
              onChange={(event) => {
                const glossary = [...(library.glossary || [])];
                glossary[index] = { ...glossary[index], term: event.target.value };
                setLibrary({ ...library, glossary });
              }}
            />
            <Input
              value={entry.meaning || ''}
              placeholder={i18n('setting.concepts.meaningPlaceholder')}
              onChange={(event) => {
                const glossary = [...(library.glossary || [])];
                glossary[index] = { ...glossary[index], meaning: event.target.value };
                setLibrary({ ...library, glossary });
              }}
            />
            <Button
              type="text"
              icon={<DeleteOutlined />}
              onClick={() =>
                setLibrary({ ...library, glossary: (library.glossary || []).filter((_, i) => i !== index) })
              }
            />
          </div>
        ))}
      </div>

      <input
        ref={fileInput}
        type="file"
        accept=".json"
        className={styles.hiddenInput}
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file) {
            importLibrary(file);
          }
        }}
      />
    </div>
  );
};

export default ConceptSetting;
