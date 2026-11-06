<script setup>
import { computed } from 'vue'

const props = defineProps({ draft: { type: Object, required: true } })
const emit = defineEmits(['cancel'])
const visible = computed({
  get: () => props.draft.leaving || !!props.draft.pending,
  set: value => {
    if (value) return
    if (props.draft.leaving) props.draft.resolveLeave('stay')
    else if (props.draft.pending) emit('cancel')
  }
})
const restore = () => {
  if (props.draft.leaving) props.draft.resolveLeave('keep')
  else props.draft.restore()
  return false
}
const buttons = computed(() => [{
  labelKey: props.draft.leaving ? 'api.label.discardDraftAndLeave' : 'api.label.discardDraft',
  type: 'danger',
  click: () => props.draft.leaving ? props.draft.resolveLeave('discard') : props.draft.discard()
}])
</script>

<template>
  <common-window
    v-model="visible"
    :title="$t(draft.leaving ? 'common.label.reminder' : 'api.label.restoreDraft')"
    width="520px"
    :close-on-click-modal="false"
    :append-to-body="true"
    :ok-label="$t(draft.leaving ? 'api.label.keepDraftAndLeave' : 'api.label.restoreDraft')"
    :cancel-label="$t(draft.leaving ? 'api.label.stayEditor' : 'api.label.skipDraftEditing')"
    :ok-click="restore"
    :buttons="buttons"
  >
    <div>
      <p>{{ $t(draft.leaving ? 'api.msg.draftLeave' : draft.conflict ? 'api.msg.draftConflict' : 'api.msg.draftFound') }}</p>
      <el-text
        v-if="draft.error"
        type="warning"
      >
        {{ $t('api.msg.draftFailed') }}
      </el-text>
    </div>
  </common-window>
</template>
