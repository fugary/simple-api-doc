<script setup>
import { computed } from 'vue'
const props = defineProps({ draft: { type: Object, required: true } })
const state = computed(() => props.draft.loadError ? 'LoadFailed' : props.draft.error ? 'Failed' : props.draft.saved ? 'Saved' : 'Pending')
</script>
<template>
  <span
    v-if="!draft.pending && (draft.dirty || draft.error || draft.loadError)"
    class="doc-draft-status"
  >
    <el-tooltip
      :content="$t('api.msg.draft' + state)"
      placement="top"
    >
      <el-text
        :type="draft.error || draft.loadError ? 'warning' : 'info'"
        size="small"
      >
        {{ $t('api.label.draft' + state) }}
      </el-text>
    </el-tooltip>
    <el-button
      v-if="draft.loadError"
      link
      type="primary"
      @click="draft.retry"
    >
      {{ $t('api.label.draftRetry') }}
    </el-button>
  </span>
</template>
<style scoped>
.doc-draft-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
}
</style>
