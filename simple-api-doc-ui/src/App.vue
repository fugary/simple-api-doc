<script setup>
import { useGlobalConfigStore } from '@/stores/GlobalConfigStore'
import { $changeLocale, elementLocale } from '@/messages'
import { useTitle } from '@vueuse/core'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { calcRouteTitle, useRoutePopStateEvent } from '@/route/RouteUtils'

const globalConfigStore = useGlobalConfigStore()
$changeLocale(globalConfigStore.currentLocale)
const route = useRoute()
const title = computed(() => calcRouteTitle(route))
useTitle(title)
useRoutePopStateEvent()
</script>

<template>
  <el-config-provider :locale="elementLocale.localeData">
    <router-view />
  </el-config-provider>
  <el-backtop
    v-common-tooltip="$t('common.label.backtop')"
    :right="70"
    :bottom="70"
  />
</template>

<style scoped>
</style>
