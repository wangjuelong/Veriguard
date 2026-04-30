// 二开移除 Channel — 占位图标直至前端深度解耦。
import { ForumOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

interface Props {
  type?: string;
  size?: 'small' | 'medium' | 'large';
}

const ChannelIcon: FunctionComponent<Props> = ({ size = 'medium' }) => {
  return <ForumOutlined fontSize={size} />;
};

export default ChannelIcon;
