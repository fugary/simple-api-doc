import { streamAiRequest } from '@/api/AiStreamApi'

export const streamAgentChat = (params, callbacks = {}, signal = null) =>
  streamAiRequest(
    `/${params.shareId ? 'shares' : 'admin'}/ai/agent/chat`,
    params, callbacks, signal, params.shareId
  )
