<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
const shareId = route.params.shareId
console.log('=======================shareId', shareId)
</script>

<template>
  Test share
</template>

<style scoped>

</style>
