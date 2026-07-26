import { i18n } from '@/i18n';
import { useGlobalStore } from '@/store/global';
import { IconfontSvg } from '@chat2db/ui';
import { Tooltip } from 'antd';
import { useStyles } from './style';

/**
 * Settings entry at the foot of the collapsed rail.
 *
 * This used to render the Chat2DB brand mark. The mark is gone, but the button
 * stays: it is the only way into Settings while the sidebar is collapsed, so
 * removing it outright would strand the user. It now shows the same settings
 * glyph the expanded rail uses, which also makes what it does obvious.
 */
const OfflineAvatar = () => {
  const { styles } = useStyles();
  const { setSettingPageActiveTab } = useGlobalStore((state) => {
    return {
      setSettingPageActiveTab: state.setSettingPageActiveTab,
    };
  });

  const handleClick = () => {
    setSettingPageActiveTab('basic');
  };

  return (
    <Tooltip title={i18n('setting.title.setting')} placement="right">
      <div className={styles.settingsButton} onClick={handleClick}>
        <IconfontSvg code="icon-adjustments" size={20} />
      </div>
    </Tooltip>
  );
};

export default OfflineAvatar;
