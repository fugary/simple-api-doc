<script setup>
import { $copyText, copyRichText } from '@/utils'
import { ElMessage } from 'element-plus'
import { $i18nBundle } from '@/messages'

const props = defineProps({
  markdown: { type: String, default: '' },
  title: { type: String, default: '' },
  bottom: { type: Number, default: 140 },
  getPreview: { type: Function, required: true }
})

/** Extract the rendered document fragment for rich-text copying. */
const buildCopyHtml = (preview, title) => {
  const source = preview?.querySelector('.md-editor-preview')
  if (!source) throw new Error('Document preview is unavailable')
  const content = source.cloneNode(true)
  content.querySelectorAll('.md-editor-code-head, .md-editor-copy-button').forEach(node => node.remove())
  // Copy page-local model references as text, retaining external links.
  content.querySelectorAll('a[href]').forEach(link => {
    if (link.getAttribute('href').trim().startsWith('#')) link.replaceWith(...link.childNodes)
  })
  if (title) {
    const heading = document.createElement('h1')
    heading.textContent = title
    content.prepend(heading)
  }
  content.style.cssText = 'color: #222; background-color: #fff; font-family: Arial, sans-serif; font-size: 11pt; line-height: 1.5;'
  const styles = {
    table: 'border-collapse: collapse; width: 100%; margin: 12px 0;',
    'th, td': 'border: 1px solid #999; padding: 6px 8px; vertical-align: top;',
    th: 'background-color: #f2f2f2; font-weight: bold;',
    pre: 'white-space: pre-wrap; background-color: #f5f5f5; padding: 10px;',
    code: 'font-family: Consolas, monospace;',
    img: 'max-width: 100%;'
  }
  for (const [selector, style] of Object.entries(styles)) {
    content.querySelectorAll(selector).forEach(node => { node.style.cssText += style })
  }
  return `<html><head><meta charset="utf-8"></head><body>${content.outerHTML}</body></html>`
}

const handleCopy = async (format) => {
  const text = props.title ? `# ${props.title}\n\n${props.markdown}` : props.markdown
  if (format === 'markdown') {
    $copyText(text)
    return
  }
  try {
    await copyRichText({ html: buildCopyHtml(props.getPreview(), props.title), text })
    ElMessage.success($i18nBundle('common.msg.copySuccess'))
  } catch {
    ElMessage.error($i18nBundle('api.msg.copyRichTextFailed'))
  }
}
</script>

<template>
  <el-dropdown
    trigger="hover"
    placement="top-end"
    @command="handleCopy"
  >
    <div
      class="floating-action-btn copy-md-btn"
      :style="{ bottom: `${bottom}px` }"
      role="button"
      tabindex="0"
      :aria-label="$t('common.label.copy')"
    >
      <common-icon
        icon="CopyDocument"
        :size="16"
      />
    </div>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="richText">
          {{ $t('api.label.copyRichText') }}
        </el-dropdown-item>
        <el-dropdown-item command="markdown">
          {{ $t('api.label.copyMarkdown') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>
