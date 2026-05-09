import { useNavigate } from 'react-router';

import { importingAttackChainRun } from '../../../actions/AttackChainRun';
import ImportUploader from '../../../components/common/ImportUploader';
import { useAppDispatch } from '../../../utils/hooks';

const ImportUploaderAttackChainRun = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const handleUpload = async (formData: FormData) => {
    await dispatch(importingAttackChainRun(formData)).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        navigate(0);
      }
    });
  };

  return (
    <ImportUploader title="Import a attack_chain_run" handleUpload={handleUpload} />
  );
};

export default ImportUploaderAttackChainRun;
