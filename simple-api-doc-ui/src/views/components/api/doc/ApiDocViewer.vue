<script setup>
import { ref, watch } from 'vue'
import { loadShareDoc } from '@/api/SimpleShareApi'
import { loadDoc } from '@/api/ApiDocApi'

const props = defineProps({
  shareId: {
    type: String,
    default: ''
  }
})

const apiDoc = defineModel({
  type: Object
})

const apiDocDetail = ref({})

const loadDocDetail = () => {
  let docPromise
  if (props.shareId) {
    docPromise = loadShareDoc({ shareId: props.shareId, docId: apiDoc.value.id }, {
      loading: true
    })
  } else {
    docPromise = loadDoc(apiDoc.value.id, {
      loading: true
    })
  }
  docPromise.then(data => {
    apiDocDetail.value = data
  })
}

watch(apiDoc, loadDocDetail, { immediate: true })

</script>

<template>
  <el-container class="padding-left2 padding-right2">
    {{ apiDocDetail }}
    {{ apiDoc }}
  </el-container>
</template>

<style scoped>

</style>
