import { InputLabel } from '@mui/material';

import CKEditor from '../CKEditor';

const SimpleRichTextField = (props) => {
  const {
    label,
    value,
    onChange = () => {},
    style,
    disabled,
    onBlur = () => {},
  } = props;
  return (
    <div style={{
      ...style,
      position: 'relative',
    }}
    >
      <InputLabel
        variant="standard"
        shrink={true}
        disabled={disabled}
      >
        {label}
      </InputLabel>
      <CKEditor
        data={value}
        onChange={(_, editor) => {
          onChange(editor.getData());
        }}
        onBlur={onBlur}
        disabled={disabled}
        toolbarDropdownSize="386px" // set a size for the ckeditor items toolbar to avoid it to be cut off when overflowing
      />
    </div>
  );
};

export default SimpleRichTextField;
