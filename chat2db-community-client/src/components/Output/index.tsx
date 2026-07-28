import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import classnames from 'classnames';
import ScrollLoading from '@/components/ScrollLoading';
import historyService, { IHistoryRecord, OperationTypeEnum } from '@/service/history';
import i18n from '@/i18n';
import { useStyles } from './style';
import { IconButton, IconfontSvg, staticMessage } from '@chat2db/ui';
import { Tooltip } from 'antd';
import { ConsoleStatus, getDatabaseInfo, WorkspaceTabType } from '@/constants';
import { useWorkspaceStore } from '@/store/workspace';
import { copyToClipboard, getTemporaryId } from '@/utils';
import { useTreeStore } from '@/store/tree';
import { TreeNodeData } from '@/typings';

interface IProps {
  className?: string;
}

type IDatasource = IHistoryRecord;

function normalizeStatus(status?: string | null) {
  return String(status || '').toLowerCase();
}

function isSuccessStatus(status?: string | null) {
  const normalizedStatus = normalizeStatus(status);
  return !normalizedStatus || normalizedStatus === 'success' || normalizedStatus === 'successful';
}

function getSqlSummary(sql?: string | null) {
  const normalizedSql = (sql || '').replace(/\s+/g, ' ').trim();
  return normalizedSql || '--';
}

function getHistoryRowKey(item: IDatasource) {
  return String(item.id || `${item.gmtCreate}-${item.dataSourceName}-${item.ddl}`);
}

function getHistorySourceKey(item: IDatasource) {
  return item.dataSourceId ? String(item.dataSourceId) : '';
}

interface IHistorySqlProps {
  /** The list's copy: whitespace intact, and cut off at 200 characters. */
  previewSql?: string | null;
  /** Set once the whole statement has been fetched, which also means expanded. */
  fullSql?: string;
  /** The server marked this row's SQL as cut short. */
  hasMore?: boolean;
  onToggle: (event: React.MouseEvent) => void;
}

/**
 * One statement in the execution log.
 *
 * Collapsed it is a two-line summary, which is what makes the list scannable;
 * the trouble was that there was no way out of it. The list endpoint sends only
 * the first 200 characters, and the summary clamps whatever arrives to two
 * lines, so a statement of any size read as an unexplained "...". Expanding
 * fetches the rest and shows it as written, line breaks and all.
 */
const HistorySql = memo<IHistorySqlProps>(({ previewSql, fullSql, hasMore, onToggle }) => {
  const { styles } = useStyles();
  const textRef = useRef<HTMLDivElement>(null);
  const [clamped, setClamped] = useState(false);
  const expanded = fullSql !== undefined;

  // Whether two lines were enough depends on the panel's width, which the user
  // can drag, so this is measured rather than guessed at from the text length.
  useEffect(() => {
    const element = textRef.current;
    if (!element || expanded) {
      return;
    }
    const measure = () => setClamped(element.scrollHeight - element.clientHeight > 1);
    measure();
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [previewSql, expanded]);

  return (
    <div className={styles.sqlBlock}>
      <div ref={textRef} className={expanded ? styles.sqlFull : styles.sqlSummary}>
        {expanded ? fullSql : getSqlSummary(previewSql)}
      </div>
      {(expanded || clamped || hasMore) && (
        <button type="button" className={styles.sqlToggle} onClick={onToggle}>
          {expanded ? i18n('common.button.showLess') : i18n('common.button.showFullSql')}
        </button>
      )}
    </div>
  );
});

function getHistoryDataSourceFallback(item: IDatasource) {
  return item.dataSourceId ? `DataSource #${item.dataSourceId}` : '-';
}

function getHistoryDataSourceName(item: IDatasource, sourceInfo?: TreeNodeData, cachedSourceName?: string) {
  return (
    item.dataSourceName ||
    cachedSourceName ||
    sourceInfo?.extraParams?.dataSourceName ||
    getHistoryDataSourceFallback(item)
  );
}

function getHistoryTitle(item: IDatasource, sourceInfo?: TreeNodeData, cachedSourceName?: string) {
  const dataSourceName =
    getHistoryDataSourceName(item, sourceInfo, cachedSourceName);
  const nameList = [dataSourceName, item.databaseName || item.schemaName].filter(Boolean);
  return nameList.join(' / ');
}

function getHistoryPopover(item: IDatasource, sourceInfo?: TreeNodeData, cachedSourceName?: string) {
  return [
    getHistoryTitle(item, sourceInfo, cachedSourceName),
    item.gmtCreate,
  ].filter(Boolean).join('\n');
}

export default memo<IProps>((props) => {
  const {
    styles,
    theme: { appearance },
  } = useStyles();
  const { className } = props;
  const addWorkspaceTab = useWorkspaceStore((state) => state.addWorkspaceTab);
  const workspaceTabList = useWorkspaceStore((state) => state.workspaceTabList);
  const savedConsoleList = useWorkspaceStore((state) => state.savedConsoleList);
  const dataSourceList = useTreeStore((state) => state.dataSourceList);
  const [dataSource, setDataSource] = useState<IDatasource[]>([]);
  const [finished, setFinished] = useState(false);
  // Row key -> the whole statement. A key being present is what "expanded"
  // means, so the fetched text is kept for as long as the row stays open.
  const [expandedSql, setExpandedSql] = useState<Record<string, string>>({});
  const outputContentRef = useRef<HTMLDivElement>(null);
  const curPageRef = useRef(1);
  const loadingRef = useRef(false);
  const finishedRef = useRef(false);

  const dataSourceInfoMap = useMemo(() => {
    return (dataSourceList || []).reduce<Record<string, TreeNodeData>>((map, item) => {
      if (item.extraParams?.dataSourceId) {
        map[String(item.extraParams.dataSourceId)] = item;
      }
      return map;
    }, {});
  }, [dataSourceList]);

  const dataSourceNameMap = useMemo(() => {
    const map: Record<string, string> = {};
    (dataSourceList || []).forEach((item) => {
      const dataSourceId = item.extraParams?.dataSourceId;
      const dataSourceName = item.extraParams?.dataSourceName;
      if (dataSourceId && dataSourceName) {
        map[String(dataSourceId)] = dataSourceName;
      }
    });
    (workspaceTabList || []).forEach((item) => {
      const dataSourceId = item.uniqueData?.dataSourceId;
      const dataSourceName = item.uniqueData?.dataSourceName;
      if (dataSourceId && dataSourceName && !map[String(dataSourceId)]) {
        map[String(dataSourceId)] = dataSourceName;
      }
    });
    (savedConsoleList || []).forEach((item) => {
      if (item.dataSourceId && item.dataSourceName && !map[String(item.dataSourceId)]) {
        map[String(item.dataSourceId)] = item.dataSourceName;
      }
    });
    return map;
  }, [dataSourceList, savedConsoleList, workspaceTabList]);

  const getFullHistoryRecord = useCallback(async (item: IDatasource) => {
    if (item.more && item.id) {
      return historyService.getHistoryDetail({ id: item.id });
    }
    return item;
  }, []);

  const toggleSqlExpanded = useCallback(
    async (event: React.MouseEvent, item: IDatasource) => {
      // The row itself opens the statement in a console tab. Expanding in place
      // is the other thing someone might want, so it must not do both.
      event.stopPropagation();
      const rowKey = getHistoryRowKey(item);
      if (expandedSql[rowKey] !== undefined) {
        setExpandedSql((prev) => {
          const next = { ...prev };
          delete next[rowKey];
          return next;
        });
        return;
      }
      const detail = await getFullHistoryRecord(item);
      setExpandedSql((prev) => ({ ...prev, [rowKey]: detail.ddl || item.ddl || '' }));
    },
    [expandedSql, getFullHistoryRecord],
  );

  const getHistoryList = useCallback(async () => {
    if (loadingRef.current || finishedRef.current) {
      return;
    }
    loadingRef.current = true;
    try {
      const res = await historyService.getHistoryList({
        pageNo: curPageRef.current,
        pageSize: 40,
        operationType: OperationTypeEnum.SQL_EXECUTE,
      });

      curPageRef.current += 1;
      finishedRef.current = !res.hasNextPage;
      setFinished(finishedRef.current);
      setDataSource((prev) => [...prev, ...((res.data || []) as IDatasource[])]);
    } finally {
      loadingRef.current = false;
    }
  }, []);

  const refresh = useCallback(() => {
    curPageRef.current = 1;
    loadingRef.current = false;
    finishedRef.current = false;
    setFinished(false);
    setDataSource([]);
    getHistoryList();
  }, [getHistoryList]);

  const openHistoryTab = useCallback(
    async (item: IDatasource) => {
      const detail = await getFullHistoryRecord(item);
      const tabId = getTemporaryId(`execution-log-${item.id || Date.now()}`);
      const sourceInfo =
        dataSourceInfoMap[getHistorySourceKey(detail)] || dataSourceInfoMap[getHistorySourceKey(item)];
      const cachedSourceName =
        dataSourceNameMap[getHistorySourceKey(detail)] || dataSourceNameMap[getHistorySourceKey(item)];
      const dataSourceName = getHistoryDataSourceName(detail, sourceInfo, cachedSourceName);
      const title = getHistoryTitle(detail, sourceInfo, cachedSourceName);
      const popoverContent = getHistoryPopover(detail, sourceInfo, cachedSourceName);

      addWorkspaceTab({
        id: tabId,
        type: WorkspaceTabType.CONSOLE,
        title,
        uniqueData: {
          consoleId: tabId,
          dataSourceId: detail.dataSourceId || undefined,
          dataSourceName: dataSourceName === '-' ? undefined : dataSourceName,
          databaseType: detail.type || sourceInfo?.extraParams?.databaseType || undefined,
          databaseName: detail.databaseName || undefined,
          schemaName: detail.schemaName || undefined,
          status: ConsoleStatus.DRAFT,
          ddl: detail.ddl || '',
          connectable: detail.connectable ?? undefined,
          popoverContent,
          readOnly: true,
        },
      });
    },
    [addWorkspaceTab, dataSourceInfoMap, dataSourceNameMap, getFullHistoryRecord],
  );

  const copyHistorySql = useCallback(
    async (event: React.MouseEvent, item: IDatasource) => {
      event.stopPropagation();
      const detail = await getFullHistoryRecord(item);
      copyToClipboard(detail.ddl || '');
      staticMessage.success(i18n('common.button.copySuccessfully'));
    },
    [getFullHistoryRecord],
  );

  const renderDatabaseIcon = useCallback(
    (item: IDatasource) => {
      const sourceInfo = dataSourceInfoMap[getHistorySourceKey(item)];
      const databaseInfo = getDatabaseInfo(item.type || sourceInfo?.extraParams?.databaseType);
      if (!databaseInfo?.icon) {
        return <IconfontSvg className={styles.databaseFallbackIcon} size={18} code="icon-chat-database" />;
      }

      return (
        <IconfontSvg
          className={styles.databaseIconSvg}
          size={22}
          existDark={databaseInfo.iconExistDark}
          appearance={appearance}
          code={databaseInfo.icon}
        />
      );
    },
    [appearance, dataSourceInfoMap, styles.databaseFallbackIcon, styles.databaseIconSvg],
  );

  const emptyContent = useMemo(() => {
    if (dataSource.length || !finished) {
      return null;
    }
    return <div className={styles.emptyContent}>{i18n('common.text.noData')}</div>;
  }, [dataSource.length, finished, styles.emptyContent]);

  return (
    <div className={classnames(styles.output, className)}>
      <div className={styles.outputTitle}>
        <span>{i18n('common.title.executiveLogging')}</span>
        <IconButton size={18} code="icon-refresh" onClick={refresh} />
      </div>
      <div className={styles.outputContent} ref={outputContentRef}>
        <ScrollLoading
          onReachBottom={getHistoryList}
          scrollerElement={outputContentRef}
          threshold={300}
          finished={finished}
        >
          <>
            {dataSource.map((item) => {
              const sourceInfo = dataSourceInfoMap[getHistorySourceKey(item)];
              const dataSourceName = getHistoryDataSourceName(
                item,
                sourceInfo,
                dataSourceNameMap[getHistorySourceKey(item)],
              );
              const sqlScope = [item.databaseName, item.schemaName].filter(Boolean).join(' / ');
              const statusIsSuccess = isSuccessStatus(item.status);
              const rowKey = getHistoryRowKey(item);
              return (
                <div
                  key={rowKey}
                  className={styles.outputItem}
                  onClick={() => openHistoryTab(item)}
                >
                  <div className={styles.recordMain}>
                    <div className={styles.databaseIcon}>{renderDatabaseIcon(item)}</div>
                    <div className={styles.recordInfo}>
                      <div className={styles.datasourceLine}>
                        <Tooltip title={dataSourceName}>
                          <span className={styles.datasourceName}>{dataSourceName}</span>
                        </Tooltip>
                        {sqlScope && (
                          <Tooltip title={sqlScope}>
                            <span className={styles.sqlScope}>{sqlScope}</span>
                          </Tooltip>
                        )}
                      </div>
                      <HistorySql
                        previewSql={item.ddl}
                        fullSql={expandedSql[rowKey]}
                        hasMore={item.more}
                        onToggle={(event) => toggleSqlExpanded(event, item)}
                      />

                      <div className={styles.metaLine}>
                        <span>{item.gmtCreate}</span>
                        {!!item.useTime && <span>{i18n('common.text.executionTime', item.useTime)}</span>}
                        {!!item.operationRows && <span>{item.operationRows} rows</span>}
                        <span
                          className={classnames(styles.statusText, {
                            [styles.failureStatusText]: !statusIsSuccess,
                          })}
                        >
                          {statusIsSuccess ? i18n('common.text.successful') : i18n('common.text.failure')}
                        </span>
                      </div>
                    </div>
                  </div>
                  <div className={classnames(styles.recordActions, 'output-record-actions')}>
                    <IconButton
                      className={styles.actionButton}
                      code="icon-copy"
                      size="sm"
                      title={i18n('common.button.copy')}
                      onClick={(event) => copyHistorySql(event, item)}
                    />
                  </div>
                </div>
              );
            })}
            {emptyContent}
          </>
        </ScrollLoading>
      </div>
    </div>
  );
});
