import Dialog from '../../../../../components/common/dialog/Dialog';
import type { InjectExpectationResult } from '../../../../../utils/api-types';
import { type InjectExpectationsStore } from '../../../common/attack_chain_nodes/expectations/Expectation';
import { isManualExpectation } from '../../../common/attack_chain_nodes/expectations/ExpectationUtils';
import DetectionPreventionExpectationsValidationForm
  from '../../../attack_chain_runs/attack_chain_run/validation/expectations/DetectionPreventionExpectationsValidationForm';
import ManualExpectationsValidationForm
  from '../../../attack_chain_runs/attack_chain_run/validation/expectations/ManualExpectationsValidationForm';

interface Props {
  open: boolean;
  injectExpectation: InjectExpectationsStore | null;
  sourceIds: string[];
  resultToEdit?: InjectExpectationResult | null;
  onClose: () => void;
  onUpdate: () => void;
}
const EditInjectExpectationResultDialog = ({ open, injectExpectation, sourceIds, resultToEdit, onClose, onUpdate }: Props) => {
  return (
    <Dialog
      open={open}
      handleClose={onClose}
    >
      {injectExpectation && (
        <>
          {isManualExpectation(injectExpectation.inject_expectation_type)
            && <ManualExpectationsValidationForm expectation={injectExpectation} onUpdate={onUpdate} />}
          {['DETECTION', 'PREVENTION'].includes(injectExpectation.inject_expectation_type)
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

export default EditInjectExpectationResultDialog;
