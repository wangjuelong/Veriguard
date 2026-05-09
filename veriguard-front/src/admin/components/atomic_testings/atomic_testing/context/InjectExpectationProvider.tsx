import { type ReactNode, useContext, useMemo, useState } from 'react';

import { fetchAttackChainNodeResultOverviewOutput } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { deleteAttackChainNodeExpectationResult } from '../../../../../actions/AttackChainRun';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import { type AttackChainNodeExpectationResult, type AttackChainNodeResultOverviewOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import type { AttackChainNodeExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';
import {
  AttackChainNodeResultOverviewOutputContext,
  type AttackChainNodeResultOverviewOutputContextType,
} from '../../AttackChainNodeResultOverviewOutputContext';
import EditAttackChainNodeExpectationResultDialog from '../target_result/EditAttackChainNodeExpectationResultDialog';
import TargetResultsSecurityPlatform from '../TargetResultsSecurityPlatform';
import AttackChainNodeExpectationContext from './AttackChainNodeExpectationContext';

const AttackChainNodeExpectationProvider = ({ children, node }: {
  children: ReactNode;
  node: AttackChainNodeResultOverviewOutput;
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [openEditResult, setOpenEditResult] = useState<boolean>(false);
  const [selectedResult, setSelectedResult] = useState<AttackChainNodeExpectationResult | null>(null);
  const [selectedAttackChainNodeExpectation, setSelectedAttackChainNodeExpectation] = useState<AttackChainNodeExpectationsStore | null>(null);
  const [openDeleteResult, setOpenDeleteResult] = useState<boolean>(false);
  const [openSecurityPlatform, setOpenSecurityPlatform] = useState<boolean>(false);

  const { updateAttackChainNodeResultOverviewOutput } = useContext<AttackChainNodeResultOverviewOutputContextType>(AttackChainNodeResultOverviewOutputContext);

  // -- Delete AttackChainNode Expectation Result
  const onOpenDeleteAttackChainNodeExpectationResult = (injectExpectationResult: AttackChainNodeExpectationResult | null = null, injectExpectationStore: AttackChainNodeExpectationsStore | null = null) => {
    setSelectedResult(injectExpectationResult);
    setSelectedAttackChainNodeExpectation(injectExpectationStore);
    setOpenDeleteResult(true);
  };
  const onCloseDeleteAttackChainNodeExpectationResult = () => {
    setSelectedResult(null);
    setSelectedAttackChainNodeExpectation(null);
    setOpenDeleteResult(false);
  };
  const onDelete = () => {
    dispatch(deleteAttackChainNodeExpectationResult(selectedAttackChainNodeExpectation?.node_expectation_id ?? '', selectedResult?.sourceId ?? '')).then(() => {
      fetchAttackChainNodeResultOverviewOutput(node.node_id).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
        updateAttackChainNodeResultOverviewOutput(result.data);
        onCloseDeleteAttackChainNodeExpectationResult();
      });
    });
  };

  // -- Create or Update AttackChainNode Expectation Result
  const onOpenEditAttackChainNodeExpectationResultResult = (result: AttackChainNodeExpectationResult | null = null, injectExpectationStore: AttackChainNodeExpectationsStore | null = null) => {
    setSelectedResult(result);
    setSelectedAttackChainNodeExpectation(injectExpectationStore);
    setOpenEditResult(true);
  };
  const onCloseEditAttackChainNodeExpectationResultResult = () => {
    setSelectedResult(null);
    setSelectedAttackChainNodeExpectation(null);
    setOpenEditResult(false);
  };
  const onUpdateValidation = () => {
    fetchAttackChainNodeResultOverviewOutput(node.node_id).then((result: { data: AttackChainNodeResultOverviewOutput }) => {
      updateAttackChainNodeResultOverviewOutput(result.data);
      onCloseEditAttackChainNodeExpectationResultResult();
    });
  };

  const onOpenSecurityPlatform = (result: AttackChainNodeExpectationResult | null = null, injectExpectationStore: AttackChainNodeExpectationsStore | null = null) => {
    setSelectedResult(result);
    setSelectedAttackChainNodeExpectation(injectExpectationStore);
    setOpenSecurityPlatform(true);
  };

  const onCloseSecurityPlatformResult = () => {
    setSelectedResult(null);
    setSelectedAttackChainNodeExpectation(null);
    setOpenSecurityPlatform(false);
  };

  const computeExistingSourceIds = (results: AttackChainNodeExpectationResult[]) => {
    const sourceIds: string[] = [];
    results.forEach((result) => {
      if (result.sourceId) {
        sourceIds.push(result.sourceId);
      }
    });
    return sourceIds;
  };

  const contextValue = useMemo(() => ({
    onOpenDeleteAttackChainNodeExpectationResult,
    onOpenEditAttackChainNodeExpectationResultResult,
    onOpenSecurityPlatform,
  }),
  [onOpenDeleteAttackChainNodeExpectationResult,
    onOpenEditAttackChainNodeExpectationResultResult,
    onOpenSecurityPlatform]);

  return (
    <AttackChainNodeExpectationContext.Provider value={contextValue}>
      {children}
      {openEditResult && (
        <EditAttackChainNodeExpectationResultDialog
          open={openEditResult}
          injectExpectation={selectedAttackChainNodeExpectation}
          sourceIds={computeExistingSourceIds(selectedAttackChainNodeExpectation?.node_expectation_results ?? [])}
          onClose={onCloseEditAttackChainNodeExpectationResultResult}
          onUpdate={onUpdateValidation}
          resultToEdit={selectedResult}
        />
      )}
      {openDeleteResult && (
        <DialogDelete
          open={openDeleteResult}
          handleClose={onCloseDeleteAttackChainNodeExpectationResult}
          text={t('Do you want to delete this expectation result?')}
          handleSubmit={onDelete}
        />
      ) }
      {selectedAttackChainNodeExpectation && (
        <TargetResultsSecurityPlatform
          injectExpectation={selectedAttackChainNodeExpectation}
          sourceId={selectedResult?.sourceId ?? ''}
          expectationResult={selectedResult}
          open={openSecurityPlatform}
          handleClose={onCloseSecurityPlatformResult}
        />
      )}
    </AttackChainNodeExpectationContext.Provider>
  );
};

export default AttackChainNodeExpectationProvider;
