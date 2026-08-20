import { memo } from 'react';
import { Button } from 'antd';
import i18n from '@/i18n';
import { useStyles } from './style';
import classnames from 'classnames';
import { useGlobalStore } from '@/store/global';
import { IframeType } from '@/constants';
import { useProductName } from '@/hooks/useProductName';
import ProductLogo from '@/components/Logo';
import { IconfontSvg } from '@chat2db/ui';

interface IProps {
  className?: string;
  slot: any;
}

export default memo<IProps>((props) => {
  const productName = useProductName();
  const { className, slot } = props;
  const { styles } = useStyles();
  const { isEmbedIframe, dismissed, setWorkspaceAiIntroDismissed } = useGlobalStore((state) => ({
    isEmbedIframe: state.isEmbedIframe,
    dismissed: state.workspaceAiIntroDismissed,
    setWorkspaceAiIntroDismissed: state.setWorkspaceAiIntroDismissed,
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

  return (
    <div className={classnames(styles.box, className)}>
      {/* No shortcut list here. It was hardcoded rather than read from the
          shortcut settings, and had drifted: "create console" is Ctrl + T, not
          Ctrl + Shift + L, and Ctrl + R runs the whole console rather than the
          selection. The real bindings live in Settings > Shortcuts, where they
          are also editable. */}
      <div className={styles.letterpress}>{productName}</div>
      {slot()}
    </div>
  );
});
