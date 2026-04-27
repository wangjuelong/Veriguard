import {
  Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle,
} from '@mui/material';

type Props = {
  open: boolean;
  title: string;
  message: string;
  onCancel: () => void;
  onConfirm: () => void;
};

const DeleteConfirmDialog = ({ open, title, message, onCancel, onConfirm }: Props) => (
  <Dialog open={open} onClose={onCancel}>
    <DialogTitle>{title}</DialogTitle>
    <DialogContent>
      <DialogContentText>{message}</DialogContentText>
    </DialogContent>
    <DialogActions>
      <Button onClick={onCancel}>取消</Button>
      <Button color="error" variant="contained" onClick={onConfirm}>确认删除</Button>
    </DialogActions>
  </Dialog>
);

export default DeleteConfirmDialog;
