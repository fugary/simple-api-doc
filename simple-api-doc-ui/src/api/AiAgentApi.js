import { BASE_URL } from '@/config'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'

/**
 * 流式执行 AI Agent 对话与工具调度
 *
 * @param {Object} params 请求参数 { query, projectId, configId }
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

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let currentEvent = 'message'

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) {
        currentEvent = 'message'
        continue
      }
      if (trimmed.startsWith('event:')) {
        currentEvent = trimmed.substring(6).trim()
      } else if (trimmed.startsWith('data:')) {
        const dataStr = trimmed.substring(5).trim()
        let parsedData = dataStr
        try {
          parsedData = JSON.parse(dataStr)
        } catch {
          // 保持原样
        }
        if (currentEvent === 'tool_start') {
          callbacks.onToolStart?.(parsedData)
        } else if (currentEvent === 'tool_end') {
          callbacks.onToolEnd?.(parsedData)
        } else if (currentEvent === 'related_docs') {
          callbacks.onRelatedDocs?.(parsedData)
        } else if (currentEvent === 'delta') {
          callbacks.onDelta?.(parsedData)
        } else if (currentEvent === 'finish') {
          callbacks.onFinish?.(parsedData)
        } else if (currentEvent === 'error') {
          callbacks.onError?.(parsedData)
        } else if (currentEvent === 'status') {
          callbacks.onStatus?.(parsedData)
        }
      }
    }
  }
}
