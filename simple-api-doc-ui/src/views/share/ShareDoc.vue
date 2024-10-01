<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { loadShare } from '@/api/SimpleShareApi'
const route = useRoute()
const shareId = route.params.shareId
console.log('=======================shareId', shareId)
const projectShare = ref()
loadShare(shareId).then(data => {
  projectShare.value = data
})
</script>

<template>
  {{ projectShare }}
</template>

<style scoped>

</style>
