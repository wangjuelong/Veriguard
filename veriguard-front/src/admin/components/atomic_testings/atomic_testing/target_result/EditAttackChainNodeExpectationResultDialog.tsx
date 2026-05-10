import Dialog from '../../../../../components/common/dialog/Dialog';
import type { NodeExpectationResult } from '../../../../../utils/api-types';
import DetectionPreventionExpectationsValidationForm
  from '../../../attack_chain_runs/attack_chain_run/validation/expectations/DetectionPreventionExpectationsValidationForm';
import ManualExpectationsValidationForm
  from '../../../attack_chain_runs/attack_chain_run/validation/expectations/ManualExpectationsValidationForm';
import { type AttackChainNodeExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';
import { isManualExpectation } from '../../../common/attack_chain_nodes/expectations/ExpectationUtils';

interface Props {
  open: boolean;
  injectExpectation: AttackChainNodeExpectationsStore | null;
  sourceIds: string[];
  resultToEdit?: NodeExpectationResult | null;
  onClose: () => void;
  onUpdate: () => void;
}
const EditAttackChainNodeExpectationResultDialog = ({ open, injectExpectation, sourceIds, resultToEdit, onClose, onUpdate }: Props) => {
  return (
    <Dialog
      open={open}
      handleClose={onClose}
    >
      {injectExpectation && (
        <>
          {isManualExpectation(injectExpectation.node_expectation_type)
            && <ManualExpectationsValidationForm expectation={injectExpectation} onUpdate={onUpdate} />}
          {['DETECTION', 'PREVENTION'].includes(injectExpectation.node_expectation_type)
            && (
              <DetectionPreventionExpectationsValidationForm
                expectation={injectExpectation}
                sourceIds={resultToEdit ? undefined : sourceIds}
                onUpdate={onUpdate}
                result={resultToEdit ?? undefined}
              />
            )}
        </>
      )}
    </Dialog>
  );
};

export default EditAttackChainNodeExpectationResultDialog;
