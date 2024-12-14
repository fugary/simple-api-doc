<script setup>
import ApiDocSchemaTable from '@/views/components/api/doc/comp/ApiDocSchemaTable.vue'

defineProps({
  componentSchemas: {
    type: Array,
    default: () => ([])
  }
})

</script>

<template>
  <el-container class="flex-column">
    <h3 id="api-doc-parameters">
      {{ $t('api.label.apiModel') }}
    </h3>
    <template
      v-for="schema in componentSchemas"
      :key="schema.name"
    >
      <api-doc-schema-table
        :model-value="schema"
        :component-schemas="componentSchemas"
      />
    </template>
  </el-container>
</template>

<style scoped>

</style>
