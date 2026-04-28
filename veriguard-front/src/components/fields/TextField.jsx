import { TextField as MuiTextField } from '@mui/material';
import { useWatch } from 'react-hook-form';

const TextFieldBase = ({ control, ...props }) => {
  const watchedValue = useWatch({
    control,
    name: props.inputProps?.name,
    disabled: !control,
  });

  const currentValue = props.inputProps?.name ? watchedValue : undefined;

  return (
    <MuiTextField
      {...props}
      value={currentValue ?? undefined}
    />
  );
};

const TextField = (props) => {
  return (
    <TextFieldBase {...props} />
  );
};

export default TextField;
