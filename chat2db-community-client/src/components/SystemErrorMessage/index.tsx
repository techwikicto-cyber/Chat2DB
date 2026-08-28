import React, { useLayoutEffect, useState } from 'react';
import { Button, notification, Space, Modal } from 'antd';
import i18n from '@/i18n';
import { IconType } from 'antd/es/notification/interface';
import Iconfont from '../Iconfont';
import { copyToClipboard, getApplicationMessage } from '@/utils';
import { useGlobalStore } from '@/store/global';
import { useStyles } from './style';

interface IProps {
  type?: IconType;
  message?: React.ReactNode;
  /** Error code. */
  errorCode: string;
  /** Error message. */
  errorMessage: string;
  /** Error details. */
  errorDetail: string;
  /** Issue wiki path. */
  solutionLink: string;
  /** Requested API. */
  requestUrl: string;
  /** Request parameters. */
  requestParams?: string;
}

/**
 * What a server error code means, said in the reader's language.
 *
 * The connection codes are the ones worth naming. "Connection failed" is true
 * of a database that is down and of a model gateway that is not answering, and
 * a user shown only that has no way to know which one to go and check. Anything
 * not listed keeps the code and the server's own text, which is what the
 * notification always showed.
 */
function describeError(errorCode: string, errorMessage: string): { title: string; description: string } {
  switch (errorCode) {
    // Raised by the server, where errorMessage is the driver's own text: the
    // title names what could not be reached, the description keeps the
    // technical detail for whoever has to act on it.
    case 'connection.error':
      return {
        title: i18n('common.error.databaseUnreachable'),
        description: i18n('common.error.technicalDetail', errorMessage || errorCode),
      };
    case 'connection.ssh.error':
      return {
        title: i18n('common.error.sshUnreachable'),
        description: i18n('common.error.technicalDetail', errorMessage || errorCode),
      };
    case 'connection.driver.load.error':
      return {
        title: i18n('common.error.driverLoad'),
        description: i18n('common.error.technicalDetail', errorMessage || errorCode),
      };

    // Raised inside a chat run, where errorMessage has already been written in
    // the reader's language and is the whole explanation.
    case 'ai.databaseUnreachable':
      return { title: i18n('common.error.databaseUnreachable'), description: errorMessage };
    case 'ai.modelUnreachable':
      return { title: i18n('common.error.modelUnreachable'), description: errorMessage };
    case 'ai.error.stream':
      return { title: i18n('common.error.assistantStopped'), description: errorMessage };

    default:
      return { title: errorCode, description: `${errorCode} ${errorMessage}` };
  }
}

function MyNotification() {
  const { styles } = useStyles();
  const [notificationApi, notificationDom] = notification.useNotification({
    maxCount: 2,
  });
  const setSystemErrorMessage = useGlobalStore((s) => s.setSystemErrorMessage);
  const [open, setOpen] = useState(false);
  const [props, setProps] = useState<IProps>();

  useLayoutEffect(() => {
    const systemErrorMessageApi = (myProps: IProps) => {
      const { errorCode, errorMessage, solutionLink } = myProps;
      setProps(myProps);
      const btn = (
        <Space>
          <Button
            type="link"
            size="small"
            onClick={() => {
              setOpen(true);
            }}
          >
            {i18n('common.notification.detail')}
          </Button>
          {solutionLink && (
            <Button type="link" size="small" target="_blank" href={solutionLink}>
              {i18n('common.notification.solution')}
            </Button>
          )}
        </Space>
      );

      // A raw error code is not a message. `connection.error` says nothing
      // about what the connection was to, and when the server's own lookup
      // fails the user is shown the key itself. So the codes worth
      // recognising are described here, in the reader's language, naming the
      // thing that could not be reached.
      const described = describeError(errorCode, errorMessage);

      const renderDescription = () => <div className={styles.description}>{described.description}</div>;

      const renderMessage = () => {
        return (
          <div className={styles.message}>
            <Iconfont className={styles.messageIcon} code="&#xe60c;" />
            <div className={styles.messageText}>{described.title}</div>
          </div>
        );
      };

      notificationApi.open({
        className: styles.notification,
        message: renderMessage(),
        description: renderDescription(),
        placement: 'bottomRight',
        // Stays until it is closed. Something failed and the user has to know
        // it did; four and a half seconds is long enough to miss while
        // reading the answer above it, and a failure they missed is one they
        // spend the next ten minutes puzzling over.
        duration: 0,
        btn,
      });
    };
    setSystemErrorMessage(systemErrorMessageApi);
  }, []);

  function renderModalTitle() {
    const list = [props?.errorCode, props?.errorMessage];
    return <div className={styles.modalTitle}>{list.filter((t) => t).join(':')}</div>;
  }

  function copyError() {
    const errorMessage = {
      getApplicationMessage: getApplicationMessage(),
      ...props,
    };
    copyToClipboard(JSON.stringify(errorMessage));
  }

  function renderModalFooter() {
    if (props?.requestParams) {
      return (
        <div className={styles.modalFooter} onClick={copyError}>
          <Iconfont code="&#xeb4e;" />
          {i18n('common.button.copyError')}
          <span className={styles.copyErrorTips}>{i18n('common.button.copyErrorTips')}</span>
        </div>
      );
    }
    return false;
  }

  return (
    <>
      {notificationDom}
      <Modal
        className={styles.modal}
        title={renderModalTitle()}
        open={open}
        width="70vw"
        footer={renderModalFooter()}
        onCancel={() => {
          setOpen(false);
        }}
        maskClosable={false}
        zIndex={99999}
      >
        <div className={styles.errorDetail}>{props?.errorDetail}</div>
      </Modal>
    </>
  );
}

export default MyNotification;
