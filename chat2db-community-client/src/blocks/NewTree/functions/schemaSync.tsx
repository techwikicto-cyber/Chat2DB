// Pinned form
import { Suspense, lazy } from 'react';
import { openModal } from '@/store/common/components';
import i18n from '@/i18n';
import { type ISelectDatabase } from '@/hooks/useSelectDatabase';

// Reached from the tree store, which the chat page pulls in on load. A static
// import would drag the editor that SchemaSync embeds into the first page load,
// for a modal that opens only from a tree context menu.
const SchemaSync = lazy(() => import('@/blocks/SchemaSync'));

export const openSchemaSyncModal = (params: ISelectDatabase) => {
  const handleClose = () => {
    openModal(null);
  };

  openModal({
    width: '800px',
    title: i18n('workspace.syncStructure.title'),
    headerIconCode: 'icon-schema-sync',
    content: (
      <Suspense fallback={null}>
        <SchemaSync
          initSourceData={{
            ...params,
            selectDone: true,
          }}
          onClose={handleClose}
        />
      </Suspense>
    ),
  });
};
