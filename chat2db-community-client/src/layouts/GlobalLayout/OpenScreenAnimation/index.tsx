import { memo } from 'react';
import styles from './index.less';
import classnames from 'classnames';
import { PRODUCT_NAME } from '@/constants/branding';

interface IProps {
  className?: string;
}

// Splash screen shown while the desktop build starts its backend. The brand
// mark is gone along with the rest of the Chat2DB branding, leaving the
// wordmark on its own.
export default memo<IProps>((props) => {
  const { className } = props;
  return (
    <div className={classnames(styles.openScreenAnimation, className)}>
      <div className={styles.brandName}>
        <div className={styles.textImg}>{PRODUCT_NAME}</div>
      </div>
    </div>
  );
});
