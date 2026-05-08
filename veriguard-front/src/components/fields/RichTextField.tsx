import { FormHelperText, InputLabel } from '@mui/material';
import { type CSSProperties } from 'react';
import { type Control, Controller } from 'react-hook-form';

import CKEditor from '../CKEditor';

interface Props {
  label: string;
  control: Control;
  name: string;
  style?: CSSProperties;
  disabled: boolean;
  required?: boolean;
}

const RichTextField = ({
  control,
  label,
  name,
  style = {},
  disabled,
  required,
}: Props) => {
  return (
    <div style={{
      ...style,
      position: 'relative',
    }}
    >
      <Controller
        name={name}
        control={control}
        rules={{ required: true }}
        render={({
          field: { onChange, onBlur, value },
          fieldState: { invalid, error: fieldError },
        }) => (
          <>
            <InputLabel
              variant="standard"
              shrink={true}
              disabled={disabled}
              required={required}
              error={!!fieldError}
            >
              {label}
            </InputLabel>
            <CKEditor
              data={value || ''}
              onChange={(_, editor) => {
                onChange(editor.getData());
              }}
              onBlur={onBlur}
              disabled={disabled}
            />
            {(invalid) && (
              <FormHelperText error>
                {(fieldError?.message)}
              </FormHelperText>
            )}
          </>
        )}
      />
    </div>
  );
};

export default RichTextField;
