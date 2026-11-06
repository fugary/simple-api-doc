<script setup>
import DocDraftNotice from './DocDraftNotice.vue'
import DocDraftWindow from './DocDraftWindow.vue'
import MarkdownDocEditHeader from './MarkdownDocEditHeader.vue'

const currentDoc = defineModel({ type: Object, required: true })
const docModel = defineModel('docModel', { type: Object, default: undefined })
const saving = defineModel('saving', { type: Boolean, default: false })
defineProps({ draft: { type: Object, required: true } })
defineEmits(['savedDoc'])
</script>

<template>
  <doc-draft-window
    :draft="draft"
    @cancel="currentDoc.editing = false"
  />
  <el-container
    v-loading="draft.loading"
    class="flex-column padding-left2 height100 padding-bottom2"
    :inert="saving || !!draft.pending"
  >
    <doc-draft-notice
      v-if="draft.loadError"
      :draft="draft"
    />
    <markdown-doc-edit-header
      v-if="docModel"
      v-model="currentDoc"
      v-model:doc-model="docModel"
      v-model:saving="saving"
      :before-cancel="draft.confirmLeave"
      :draft="draft"
      @saved-doc="$emit('savedDoc', $event)"
    />
    <slot v-if="docModel" />
  </el-container>
</template>
