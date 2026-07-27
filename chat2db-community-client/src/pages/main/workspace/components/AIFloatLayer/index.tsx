import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import AIChatInput, { ChatInputPropsRef, SendParams } from '@/blocks/AI/components/AIChatInput';
import { useWorkspaceStore } from '@/store/workspace';
import { ChatSourceType, QuestionType } from '@/constants/chat';
import { useStyles } from './style';
import useSSERequest, { SSERequestStatus } from '@/hooks/useSSERequest';
import useSyncState from '@/hooks/useSyncState';
import { IModelOptionItem } from '@/service/aiStream';
import { useAIStore } from '@/store/ai';
import i18n from '@/i18n';

import AIModelConfigModal from '@/blocks/AI/components/AIModelConfigModal';
import { reconcileSelectedModel } from '@/blocks/AI/components/AIModelSelect/modelSelectOptions';
import { listAvailableModelOptions, resolveModelRequestPayload } from '@/service/aiModelConfig';
import feedback from '@/utils/feedback';

interface IStreamChunk {
  type: 'reasoning' | 'tool_call' | 'tool_result' | 'answer' | 'done' | 'error';
  messageType?: 'reasoning' | 'tool_call' | 'tool_result' | 'answer' | 'done' | 'error';
  content?: string;
  sessionId?: string;
  name?: string;
  arguments?: string;
}

const AIFloatLayer = () => {
  const { styles } = useStyles();
  const { activeConsoleId, workspaceTabList, appendConsole } = useWorkspaceStore((state) => ({
    workspaceTabList: state.workspaceTabList,
    activeConsoleId: state.activeConsoleId,
    consoleAiInputParams: state.consoleAiInputParams,
    clearConsoleAiInputParams: state.clearConsoleAiInputParams,
    appendConsole: state.appendConsole,
  }));
  // Whether this is the first appendConsole call.
  const [, setIsFirstAppend, getIsFirstAppend] = useSyncState(false);
  const [modelOptions, setModelOptions] = useState<Array<{ label: string; value: string; isDefault?: boolean }>>([]);
  const [modelOptionMap, setModelOptionMap] = useState<Record<string, IModelOptionItem>>({});
  const [openSettings, setOpenSettings] = useState(false);
  const { selectedModel, setSelectedModel } = useAIStore((state) => ({
    selectedModel: state.selectedModel,
    setSelectedModel: state.setSelectedModel,
  }));

  const { uniqueData } =
    useMemo(() => {
      return workspaceTabList?.find((i) => i.id === activeConsoleId);
    }, [activeConsoleId, workspaceTabList]) || {};

  const chatInputRef = useRef<ChatInputPropsRef>(null);

  const { status, request, stop } = useSSERequest<IStreamChunk>(
    {
      baseURL: '/api/v3/ai/chat/stream',
      onChunk: (_rawData, parsedData) => {
        const chunk = parsedData as unknown as IStreamChunk;
        const messageType = chunk?.messageType || chunk?.type;
        if (messageType !== 'answer' || !chunk.content) {
          return;
        }

        const isFirstAppend = getIsFirstAppend();
        if (!activeConsoleId) {
          return;
        }

        setIsFirstAppend(false);
        appendConsole({
          id: activeConsoleId,
          content: chunk.content,
          space: isFirstAppend,
        });
      },
    },
    undefined,
  );

  const loadModelOptions = useCallback(async () => {
    try {
      const result = (await listAvailableModelOptions()) || [];
      const optionMap: Record<string, IModelOptionItem> = {};
      result.forEach((item) => {
        optionMap[item.value] = item;
      });
      setModelOptionMap(optionMap);
      setModelOptions(
        result.map((item) => ({
          label: item.label,
          value: item.value,
          isDefault: !!item.defaultOption,
        })),
      );

      const nextSelection = reconcileSelectedModel(selectedModel, result);
      if (nextSelection !== undefined) {
        setSelectedModel(nextSelection);
      }
    } catch {
      setModelOptionMap({});
      setModelOptions([]);
      feedback.error(i18n('stream.error.loadModelList'));
    }
  }, [selectedModel?.label, selectedModel?.value, setSelectedModel]);

  useEffect(() => {
    loadModelOptions();
  }, [loadModelOptions]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      chatInputRef.current?.focusInput();
    }, 0);

    return () => {
      window.clearTimeout(timer);
    };
  }, []);

  const handleSend = async (params: SendParams) => {
    const content = (params.input || '').trim();
    if (!content) {
      return;
    }

    const { model: requestedModel, input: discardedInput, ...restParams } = params;
    void discardedInput;
    const selectedValue = requestedModel || selectedModel?.value;
    if (!selectedValue) {
      feedback.warning(i18n('stream.warning.selectModel'));
      return;
    }

    const selectedOption = modelOptionMap[selectedValue];
    if (!selectedOption) {
      feedback.warning(i18n('stream.warning.invalidModel'));
      return;
    }

    setIsFirstAppend(true);
    const modelRequestPayload = await resolveModelRequestPayload(selectedOption);
    if (!modelRequestPayload) {
      feedback.warning(i18n('stream.warning.invalidModel'));
      return;
    }

    await request({
      input: content,
      enableTools: true,
      ...modelRequestPayload,
      history: [],
      dataSourceCollectionId: restParams.dataSourceCollectionId,
      dataSourceId: restParams.dataSourceId,
      databaseName: restParams.databaseName,
      schemaName: restParams.schemaName,
      tableName: restParams.tableName,
      ...restParams,
      source: ChatSourceType.DATASOURCE_CHAT,
      questionType: QuestionType.NL_2_SQL,
    });
  };

  const closeEventSource = () => {
    stop();
  };

  return (
    <>
      <AIChatInput
        ref={chatInputRef}
        className={styles.aiChatContainer}
        hideDatabaseSelect
        contextInfo={{
          dataSourceId: uniqueData?.dataSourceId,
          databaseName: uniqueData?.databaseName,
          schemaName: uniqueData?.schemaName,
        }}
        autoSize={{ minRows: 1, maxRows: 5 }}
        autoFocus
        clearAfterSend={false}
        modelOptions={modelOptions}
        loading={status === SSERequestStatus.LOADING}
        onChatSend={handleSend}
        onStop={closeEventSource}
        showCustomModelEntry
        onCustomModelClick={() => setOpenSettings(true)}
        customModelText={i18n('setting.modelConfig.entry')}
      />
      <AIModelConfigModal
        open={openSettings}
        onClose={() => setOpenSettings(false)}
        onChanged={loadModelOptions}
      />
    </>
  );
};

export default AIFloatLayer;
