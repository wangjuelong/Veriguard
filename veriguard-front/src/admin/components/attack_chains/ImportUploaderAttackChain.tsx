import { useNavigate } from 'react-router';

import { importAttackChain } from '../../../actions/attack_chains/attack_chain-actions';
import ImportUploader from '../../../components/common/ImportUploader';
import { useAppDispatch } from '../../../utils/hooks';

const ImportUploaderAttackChain = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const handleUpload = async (formData: FormData) => {
    await dispatch(importAttackChain(formData)).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        navigate(0);
      }
    });
  };

  return (
    <ImportUploader
      title="Import a attack_chain"
      handleUpload={handleUpload}
    />
  );
};

export default ImportUploaderAttackChain;
