// 二开移除 Channel — 占位 helper 类型直至前端深度解耦。
import { type Channel } from '../../utils/api-types';

export interface ChannelsHelper {
  getChannel: (channelId: string) => Channel | undefined;
  getChannels: () => Channel[];
  getChannelsMap: () => Record<string, Channel>;
}
