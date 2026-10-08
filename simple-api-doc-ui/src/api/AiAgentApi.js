import { BASE_URL } from '@/config'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { $i18nBundle } from '@/messages'

const eventCallbacks = {
  status: 'onStatus',
  tool_start: 'onToolStart',
  tool_end: 'onToolEnd',
  related_docs: 'onRelatedDocs',
  delta: 'onDelta',
  finish: 'onFinish',
  error: 'onError'
}

/**
 * 流式执行 AI Agent 对话与工具调度
 *
 * @param {Object} params 请求参数 { query, projectId, configId, model, includeProjectOverview }
 * @param {Object} callbacks 回调对象 { onStatus, onToolStart, onToolEnd, onRelatedDocs, onDelta, onFinish, onError }
 * @param {AbortSignal} [signal] 取消信号
 */
export const streamAgentChat = async (params, callbacks = {}, signal = null) => {
  const loginStore = useLoginConfigStore()

  const url = `${BASE_URL || ''}/admin/ai/agent/chat`
  const headers = {
    'Content-Type': 'application/json'
  }
  if (loginStore.accessToken) {
    headers.Authorization = `Bearer ${loginStore.accessToken}`
  }

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(params),
    signal
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || `Request failed with status ${response.status}`)
  }

  if (!response.body) throw new Error($i18nBundle('api.msg.aiStreamInterrupted'))
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let currentEvent = 'message'
  let dataLines = []

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      if (done) throw new Error($i18nBundle('api.msg.aiStreamInterrupted'))

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const rawLine of lines) {
        if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
        const line = rawLine.replace(/\r$/, '')
        if (!line) {
          // 仅分发完整 SSE 事件，避免将被截断的 finish 消息当作成功。
          if (dataLines.length) {
            let data = dataLines.join('\n')
            try {
              data = JSON.parse(data)
            } catch {
              // 保留服务端的纯文本消息。
            }
            callbacks[eventCallbacks[currentEvent]]?.(data)
            if (currentEvent === 'finish' || currentEvent === 'error') return
          }
          currentEvent = 'message'
          dataLines = []
        } else if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.substring(5).replace(/^ /, ''))
        }
      }
    }
  } finally {
    // 终止事件之后不再等待连接关闭；清理失败不能覆盖原始错误。
    reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
