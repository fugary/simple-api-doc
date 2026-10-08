import { h } from 'vue'
import { ElTag } from 'element-plus'

const AI_PROVIDER_STYLES = {
  OPENAI: { label: 'OpenAI', type: 'success' },
  ANTHROPIC: { label: 'Anthropic', type: 'warning' },
  GEMINI: { label: 'Gemini', type: 'primary' }
}

export const getAiProviderStyle = (provider) => Object.hasOwn(AI_PROVIDER_STYLES, provider)
  ? AI_PROVIDER_STYLES[provider]
  : { label: provider, type: 'info' }

export const renderAiProviderTag = (provider, attrs = {}) => {
  if (!provider) return ''
  const { label, type } = getAiProviderStyle(provider)
  return h(ElTag, { type, disableTransitions: true, ...attrs }, () => label)
}
