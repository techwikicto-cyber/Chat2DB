import React, { Suspense, lazy, useState, useEffect, useCallback } from 'react';
import { Spin, Splitter } from 'antd';
import AI, { ITableClickContext } from '@/blocks/AI';
import CustomTabs, { ITabItem } from '@/components/Tabs';
import { IViewTableParams } from '@/typings';
import { ConsoleStatus, WorkspaceTabType } from '@/constants';
import { getDatabaseSupport } from '@/utils/database';
import { useStyles } from './style';

// The right-hand pane is empty until an answer opens a table or a console, but
// these two carry the two heaviest dependencies in the product - the canvas grid
// and Monaco. Static imports put both in the first page load, for a pane nobody
// has opened yet.
const ViewTable = lazy(() => import('@/components/ViewTable'));
const SQLExecute = lazy(() => import('@/pages/main/workspace/components/SQLExecute'));

/** Shown for the moment a lazily loaded pane's chunk is in flight. */
function PaneLoading() {
  return (
    <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Spin />
    </div>
  );
}

interface IRightTab {
  key: string;
  label: string;
  type: 'table' | 'console';
  tableParams?: IViewTableParams;
  consoleParams?: {
    boundInfo: any;
    initDDL: string;
  };
}

export default function StreamPage() {
  const { styles } = useStyles();
  const [rightTabs, setRightTabs] = useState<IRightTab[]>([]);
  const [activeTabKey, setActiveTabKey] = useState<string | number | null>(null);
  const [sizes, setSizes] = useState<(number | string)[]>(['100%', 0]);

  const hasTab = rightTabs.length > 0;

  useEffect(() => {
    if (hasTab) {
      setSizes(['45%', '55%']);
    } else {
      setSizes(['100%', 0]);
    }
  }, [hasTab]);

  const buildTableKey = (t: IViewTableParams) =>
    `table-${t.dataSourceId}-${t.databaseName || ''}-${t.schemaName || ''}-${t.tableName}`;

  const handleTableClick = useCallback(
    (tableName: string, context: ITableClickContext) => {
      const params: IViewTableParams = {
        dataSourceId: context.dataSourceId,
        databaseName: context.databaseName,
        schemaName: context.schemaName,
        databaseType: context.databaseType,
        tableName,
      };
      const key = buildTableKey(params);

      if (rightTabs.some((t) => t.key === key)) {
        setActiveTabKey(key);
        return;
      }

      const label = tableName + (context.dataSourceName ? `[${context.dataSourceName}]` : '');
      const tab: IRightTab = { key, label, type: 'table', tableParams: params };
      setRightTabs((prev) => [...prev, tab]);
      setActiveTabKey(key);
    },
    [rightTabs],
  );

  const pinIdRef = React.useRef(0);

  const handlePinSql = useCallback((sql: string, context: ITableClickContext) => {
    const { dataSourceId, databaseName, schemaName, databaseType, dataSourceName } = context;
    if (!dataSourceId || !databaseType) return;

    const name = `${[databaseName || schemaName].filter(Boolean).join('-')}${
      dataSourceName ? `[${dataSourceName}]` : ''
    }`;
    const localId = --pinIdRef.current; // Use a negative value to avoid collisions with real console IDs.

    const key = `console-${localId}`;
    const boundInfo = {
      consoleId: localId,
      dataSourceId,
      dataSourceName,
      databaseType,
      databaseName,
      schemaName,
      status: ConsoleStatus.DRAFT,
      connectable: true,
      ...getDatabaseSupport(databaseType),
    };

    const tab: IRightTab = {
      key,
      label: name || 'SQL Console',
      type: 'console',
      consoleParams: { boundInfo, initDDL: sql },
    };
    setRightTabs((prev) => [...prev, tab]);
    setActiveTabKey(key);
  }, []);

  const handleTabChange = useCallback((key: string | number | null) => {
    setActiveTabKey(key);
  }, []);

  const handleTabEdit = useCallback(
    (action: 'add' | 'remove', data?: ITabItem[]) => {
      if (action === 'remove' && data) {
        const removedKeys = new Set(data.map((d) => d.key));
        const remaining = rightTabs.filter((t) => !removedKeys.has(t.key));
        setRightTabs(remaining);
        if (remaining.length === 0) {
          setActiveTabKey(null);
        }
      }
    },
    [rightTabs],
  );

  const handleSessionChange = useCallback(() => {
    setRightTabs([]);
    setActiveTabKey(null);
  }, []);

  const tabItems: ITabItem[] = rightTabs.map((tab) => ({
    key: tab.key,
    label: tab.label,
    canClosed: true,
    children:
      tab.type === 'table' && tab.tableParams ? (
        <Suspense fallback={<PaneLoading />}>
          <ViewTable key={tab.key} viewTableParams={tab.tableParams} />
        </Suspense>
      ) : tab.type === 'console' && tab.consoleParams ? (
        <Suspense fallback={<PaneLoading />}>
          <SQLExecute
            key={tab.key}
            boundInfo={tab.consoleParams.boundInfo}
            initDDL={tab.consoleParams.initDDL}
            type={WorkspaceTabType.CONSOLE}
            isActive={activeTabKey === tab.key}
            isConsole={false}
          />
        </Suspense>
      ) : null,
  }));

  return (
    <Splitter className={styles.fullContainer} onResize={setSizes}>
      <Splitter.Panel size={sizes[0]} min={hasTab ? '20%' : '100%'} max={hasTab ? '70%' : '100%'}>
        <div className={styles.paneInner}>
          <AI
            variant="page"
            onTableClick={handleTableClick}
            onPinSql={handlePinSql}
            onSessionChange={handleSessionChange}
          />
        </div>
      </Splitter.Panel>
      <Splitter.Panel size={sizes[1]} resizable={hasTab} min={hasTab ? 600 : 0}>
        {hasTab && (
          <CustomTabs
            className={styles.tableTabsContainer}
            items={tabItems}
            activeKey={activeTabKey}
            onChange={handleTabChange}
            onEdit={handleTabEdit}
            hideAdd
          />
        )}
      </Splitter.Panel>
    </Splitter>
  );
}
