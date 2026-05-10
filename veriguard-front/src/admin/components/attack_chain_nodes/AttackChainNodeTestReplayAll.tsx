import { ForwardToInbox } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import DialogTest from '../../../components/common/DialogTest';
import { useFormatter } from '../../../components/i18n';
import { type AttackChainNodeTestStatusOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { AttackChainNodeTestContext, PermissionsContext } from '../common/Context';

interface Props {
  searchPaginationInput: SearchPaginationInput;
  injectIds: string[] | undefined;
  onTest?: (result: AttackChainNodeTestStatusOutput[]) => void;
}

const AttackChainNodeTestReplayAll: FunctionComponent<Props> = ({
  searchPaginationInput,
  injectIds,
  onTest,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const [openAllTest, setOpenAllTest] = useState(false);

  const {
    contextId,
    bulkTestAttackChainNodes,
  } = useContext(AttackChainNodeTestContext);

  const handleOpenAllTest = () => {
    setOpenAllTest(true);
  };

  const handleCloseAllTest = () => {
    setOpenAllTest(false);
  };

  const handleSubmitAllTest = () => {
    if (bulkTestAttackChainNodes) {
      bulkTestAttackChainNodes(contextId, {
        search_pagination_input: searchPaginationInput,
        attack_chain_run_or_attack_chain_id: contextId,
      }!).then((result: { data: AttackChainNodeTestStatusOutput[] }) => {
        onTest?.(result.data);
        MESSAGING$.notifySuccess(t('Test(s) sent'));
        return result;
      });
    }
    handleCloseAllTest();
  };

  return (
    <>
      {permissions.canLaunch
        && (
          <Tooltip title={t('Replay all tests')}>
            <span>
              <IconButton
                aria-label="test"
                disabled={
                  injectIds?.length === 0
                }
                onClick={handleOpenAllTest}
                color="primary"
                size="small"
              >
                <ForwardToInbox fontSize="small" />
              </IconButton>
            </span>
          </Tooltip>
        )}
      <DialogTest
        open={openAllTest}
        handleClose={handleCloseAllTest}
        handleSubmit={handleSubmitAllTest}
        text={t('Do you want to replay all these tests?')}
      />
    </>

  );
};

export default AttackChainNodeTestReplayAll;
