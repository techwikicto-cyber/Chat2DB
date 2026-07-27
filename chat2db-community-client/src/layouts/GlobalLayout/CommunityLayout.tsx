import { FC, useEffect } from 'react';
import { Outlet } from 'umi';
import { Spin } from 'antd';
import { createStyles } from 'antd-style';
import AppTheme, { AppThemeProps } from '@/components/AppTheme';
import LoadingGracile from '@/components/Loading/LoadingGracile';
import ConfigProvider from '@/components/ConfigProvider';
import CommunityAuthGate from '@/blocks/CommunityAuth';
import GlobalStyle from '@/styles/global';
import GlobalComponentCommunity from '@/layouts/init/GlobalComponentCommunity';
import useInit from '../init/init';
import { useGlobalStore } from '@/store/global';
import { ServiceStatus } from '@/constants/common';
import AppTitleBar from './AppTitleBar';
import { insertOpenScreenAnimationExpand } from '@/utils/dom';
import i18n from '@/i18n';
import '@/assets/fonts/new-chat2db-iconfont';
import '@/assets/fonts/new-chat2db-colourful-iconfont';
import '@/assets/fonts/oscar-iconfont';
import { isDesktop } from '@/utils/env';

interface CommunityLayoutProps extends AppThemeProps {}

const useStyles = createStyles(({ css }) => {
  return {
    app: css`
      height: 100%;
      display: flex;
      flex-direction: column;
    `,
    appContent: css`
      flex: 1;
      height: 0px;
      position: relative;
    `,
    loadingBox: css`
      height: 100%;
      display: flex;
      justify-content: center;
      align-items: center;
    `,
    extend: css`
      display: flex;
      align-items: center;
      height: 40px;
      padding-left: 10px;
      span {
        margin-left: 10px;
      }
    `,
  };
});

const CommunityLayout: FC<CommunityLayoutProps> = () => {
  return (
    <ConfigProvider>
      <AppTheme>
        <GlobalStyle />
        {/* Outside the gate: the sign-in screen reports failures through these. */}
        <GlobalComponentCommunity />
        <CommunityAuthGate>
          <CommunityShell />
        </CommunityAuthGate>
      </AppTheme>
    </ConfigProvider>
  );
};

/**
 * Everything behind the password gate.
 *
 * Split out so `useInit` - which queries the API, restores settings and boots
 * the editor - only runs once there is a session to run it under.
 */
const CommunityShell: FC = () => {
  const { styles } = useStyles();
  const serviceStatus = useGlobalStore((state) => state.serviceStatus);

  useInit();

  useEffect(() => {
    if (isDesktop) {
      insertOpenScreenAnimationExpand(
        <div className={styles.extend}>
          <LoadingGracile />
          <span>{i18n('common.text.startingService')}</span>
        </div>,
      );
    }
  }, [styles.extend]);

  const renderApp = () => {
    if (serviceStatus === ServiceStatus.PENDING && isDesktop) {
      return (
        <div className={styles.loadingBox}>
          <Spin size="large" />
        </div>
      );
    }
    return <Outlet />;
  };

  return (
    <div className={styles.app}>
      <AppTitleBar />
      <div className={styles.appContent}>{renderApp()}</div>
    </div>
  );
};

export default CommunityLayout;
