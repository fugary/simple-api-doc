import { BASE_URL } from '@/config'
import { getShareConfig } from '@/api/SimpleShareApi'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { $i18nBundle } from '@/messages'
import { SIMPLE_SHARE_ERROR_HEADER } from '@/consts/ApiConstants'
import emitter from '@/vendors/emitter'

const eventCallbacks = {
  status: 'onStatus',
  tool_start: 'onToolStart',
  tool_end: 'onToolEnd',
  related_docs: 'onRelatedDocs',
  delta: 'onDelta',
  answer_reset: 'onAnswerReset',
  answer: 'onAnswer',
  finish: 'onFinish',
  error: 'onError'
}

/** 共用鉴权、SSE 分帧与中断检测。 */
export const streamAiRequest = async (path, params, callbacks = {}, signal = null, shareId = null) => {
  const loginStore = useLoginConfigStore()

  const url = `${BASE_URL || ''}${path}`
  const headers = {
    'Content-Type': 'application/json'
  }
  if (shareId) {
    Object.assign(headers, getShareConfig(shareId).headers)
  } else if (loginStore.accessToken) {
    headers.Authorization = `Bearer ${loginStore.accessToken}`
  }

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(params),
    signal
  })

  // 分享鉴权失败返回 HTTP 200 的 JSON，不能作为 SSE 读取。
  if (response.headers.has(SIMPLE_SHARE_ERROR_HEADER) || response.headers.get('content-type')?.includes('application/json')) {
    const data = await response.json()
    const error = Object.assign(new Error(data.message || $i18nBundle('api.msg.aiStreamInterrupted')), { data })
    if (shareId) emitter.emit('share-doc-error', error)
    throw error
  }

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
            let data
            try {
              data = JSON.parse(dataLines.join('\n'))
            } catch {
              throw new Error($i18nBundle('api.msg.aiStreamInterrupted'))
            }
            if (currentEvent === 'finish' && !['SUCCESS', 'INCOMPLETE'].includes(data?.status)) {
              throw new Error($i18nBundle('api.msg.aiStreamInterrupted'))
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
