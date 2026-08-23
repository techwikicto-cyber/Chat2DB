import { memo } from 'react';
import { Button } from 'antd';
import i18n from '@/i18n';
import { useStyles } from './style';
import classnames from 'classnames';
import { useGlobalStore } from '@/store/global';
import { useTreeStore } from '@/store/tree';
import { DatabaseTypeCode, IframeType } from '@/constants';
import { databaseMap } from '@/constants/database';
import { useProductName } from '@/hooks/useProductName';
import ProductLogo from '@/components/Logo';
import { IconfontSvg } from '@chat2db/ui';

interface IProps {
  className?: string;
  slot: any;
}

/**
 * The databases offered directly, rather than the whole list of forty.
 *
 * Picking one is the shortest path from a signed-in empty workspace to a
 * working connection, and the row doubles as the answer to "what does this
 * connect to?" - a question the old screen left to the sidebar. The full list
 * is one click away in the tree, which the closing line points at.
 */
const QUICK_CONNECT_TYPES = [
  DatabaseTypeCode.MYSQL,
  DatabaseTypeCode.POSTGRESQL,
  DatabaseTypeCode.SQLSERVER,
  DatabaseTypeCode.ORACLE,
  DatabaseTypeCode.SQLITE,
  DatabaseTypeCode.MONGODB,
];

export default memo<IProps>((props) => {
  const productName = useProductName();
  const { className, slot } = props;
  const {
    styles,
    theme: { appearance },
  } = useStyles();
  const { isEmbedIframe, dismissed, setWorkspaceAiIntroDismissed } = useGlobalStore((state) => ({
    isEmbedIframe: state.isEmbedIframe,
    dismissed: state.workspaceAiIntroDismissed,
    setWorkspaceAiIntroDismissed: state.setWorkspaceAiIntroDismissed,
  }));
  const { dataSourceList, setConnectionDetail, setIsModalVisible } = useTreeStore((state) => ({
    dataSourceList: state.dataSourceList,
    setConnectionDetail: state.setConnectionDetail,
    setIsModalVisible: state.setIsModalVisible,
  }));

  if (isEmbedIframe === IframeType.ZOER) {
    return null;
  }

  const handleGoToAI = () => {
    window.dispatchEvent(new CustomEvent('app:navigateTo', { detail: { page: 'stream' } }));
  };

  const handleDismiss = () => {
    setWorkspaceAiIntroDismissed(true);
  };

  // Same two calls the sidebar's add-connection menu makes. The dialog itself
  // lives in AddDatasourceBar and is driven entirely from this store, so it
  // opens from here without a second copy of it.
  const handleQuickConnect = (type: DatabaseTypeCode) => {
    setConnectionDetail({ type } as any);
    setTimeout(() => {
      setIsModalVisible(true);
    }, 0);
  };

  if (!dismissed) {
    return (
      <div className={classnames(styles.box, className)}>
        <div className={styles.aiIntro}>
          <div className={styles.aiIconWrap}>
            <ProductLogo size={44} />
          </div>
          <div className={styles.aiTitle}>{productName}</div>
          <div className={styles.aiDesc}>{i18n('stream.intro.desc')}</div>
          <div className={styles.featureRow}>
            <div className={styles.featureCard}>
              <IconfontSvg className={styles.featureIcon} code="icon-search" size={16} />
              <span>{i18n('stream.intro.feature.query')}</span>
            </div>
            <div className={styles.featureCard}>
              <IconfontSvg className={styles.featureIcon} code="icon-chart-square-bar" size={16} />
              <span>{i18n('stream.intro.feature.chart')}</span>
            </div>
            <div className={styles.featureCard}>
              <IconfontSvg className={styles.featureIcon} code="icon-sparkles" size={16} />
              <span>{i18n('stream.intro.feature.analysis')}</span>
            </div>
          </div>
          <Button type="primary" className={styles.aiCta} onClick={handleGoToAI}>
            {i18n('stream.intro.cta')}
            <IconfontSvg className={styles.aiCtaArrow} code="icon-right-arrow" size={14} />
          </Button>
          <button className={styles.dismissBtn} onClick={handleDismiss}>
            {i18n('stream.intro.dismiss')}
          </button>
        </div>
      </div>
    );
  }

  // null means the connection list has not come back yet, which is not the same
  // as having none. Telling a returning user to connect a database for the
  // moment before their connections load would be wrong, so wait it out.
  if (dataSourceList === null) {
    return <div className={classnames(styles.box, className)} />;
  }

  // Nothing to open a console against yet, so asking for one would be a dead
  // end. The screen asks for the thing that unblocks everything else instead.
  if (!dataSourceList.length) {
    return (
      <div className={classnames(styles.box, className)}>
        <div className={styles.stage}>
          <h2 className={styles.stageTitle}>{i18n('workspace.empty.connect.title')}</h2>
          <p className={styles.stageDesc}>{i18n('workspace.empty.connect.desc')}</p>
          <div className={styles.databaseRow}>
            {QUICK_CONNECT_TYPES.map((type) => {
              const database = databaseMap[type];
              if (!database) {
                return null;
              }
              return (
                <button
                  key={type}
                  type="button"
                  className={styles.databaseChip}
                  onClick={() => handleQuickConnect(type)}
                >
                  <IconfontSvg
                    code={database.icon}
                    existDark={database.iconExistDark}
                    appearance={appearance}
                    size={20}
                  />
                  <span>{database.name}</span>
                </button>
              );
            })}
          </div>
          <p className={styles.stageFootnote}>{i18n('workspace.empty.connect.more')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className={classnames(styles.box, className)}>
      <div className={styles.stage}>
        <h2 className={styles.stageTitle}>{i18n('workspace.empty.console.title')}</h2>
        <p className={styles.stageDesc}>{i18n('workspace.empty.console.desc')}</p>
        {/* The slot's own wrapper carries class names that are not defined
            anywhere, so it arrives with no spacing of its own. */}
        <div className={styles.stageAction}>{slot()}</div>
      </div>
    </div>
  );
});
