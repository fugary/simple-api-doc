<script setup lang="jsx">
import { computed, ref } from 'vue'
import { $i18nKey } from '@/messages'
import {
  checkAndSaveDocInfoDetail,
  getOauth2ConfigInfo,
  newDocInfoDetail,
  securitySchemas2List
} from '@/services/api/ApiDocEditService'
import { defineFormOptions } from '@/components/utils'
import { cloneDeep } from 'lodash-es'
import { ElText } from 'element-plus'
import { checkShowColumn } from '@/utils'

defineProps({
  editable: {
    type: Boolean,
    default: false
  }
})

const apiDocDetail = defineModel({
  type: Object,
  default: () => ({})
})

const securitySchemas = computed(() => {
  return apiDocDetail.value.projectInfoDetail?.securitySchemas?.filter(security => !!security.schemaContent)
    .map(security => JSON.parse(security.schemaContent))
    .reduce((res, item) => {
      return { ...res, ...item }
    }, {})
})
const securitySchemaList = computed(() => {
  return securitySchemas2List(securitySchemas.value)
})
// doc支持的
const securityRequirements = computed(() => {
  const securityRequirements = apiDocDetail.value?.securityRequirements
  return JSON.parse(securityRequirements?.schemaContent || '[]')
})
const supportedKeys = computed(() => securityRequirements.value.flatMap(config => Object.keys(config)))
const supportedSecurities = computed(() => {
  return securitySchemaList.value.filter(schema => supportedKeys.value.includes(schema.schemaName))
})
const hasSecurity = computed(() => !!supportedKeys.value?.length)
const showSecurityConfigWindow = ref(false)
const schemaEditModel = ref({
  securityParams: []
})
const emit = defineEmits(['schemaUpdated'])
const toEditSecuritySchemas = () => {
  const securityParams = cloneDeep(securitySchemaList.value)
  let hasOauth2 = false
  securityParams.forEach(securityParam => {
    securityParam.needAuth = supportedKeys.value?.includes(securityParam.schemaName)
    if (securityParam.schema?.type === 'oauth2') {
      hasOauth2 = true
      securityParam.scopes = securityRequirements.value.find(require => require[securityParam.schemaName])?.[securityParam.schemaName]
      const oauth2Config = getOauth2ConfigInfo(securityParam.schema)
      securityParam.scopesOptions = oauth2Config?.scopesOptions
    }
  })
  schemaEditModel.value.securityParams = securityParams
  schemaEditModel.value.hasOauth2 = hasOauth2
  showSecurityConfigWindow.value = true
}
const saveSecuritySchemas = () => {
  console.log('====================schemaEditModel', schemaEditModel.value)
  const newSecurities = schemaEditModel.value.securityParams.filter(securityParam => !!securityParam.needAuth).map(securityParam => {
    return {
      [securityParam.schemaName]: securityParam.scopes || []
    }
  })
  const securityRequirements = apiDocDetail.value?.securityRequirements ||
      {
        ...newDocInfoDetail(apiDocDetail.value),
        bodyType: 'doc_security_requirements'
      }
  securityRequirements.schemaContent = JSON.stringify(newSecurities)
  checkAndSaveDocInfoDetail(securityRequirements).then(() => emit('schemaUpdated'))
  showSecurityConfigWindow.value = true
}
const formOptions = computed(() => {
  return defineFormOptions([{
    labelKey: 'api.label.needAuth',
    prop: 'needAuth',
    type: 'switch',
    minWidth: '50px'
  }, {
    labelKey: 'common.label.name',
    prop: 'schemaName',
    disabled: true,
    minWidth: '100px'
  }, {
    label: 'Scopes',
    prop: 'scopes',
    minWidth: '150px',
    type: 'select',
    attrs: {
      multiple: true
    },
    enabled: schemaEditModel.value.hasOauth2,
    dynamicOption (data) {
      console.log('===============data', data)
      return {
        enabled: data.schema?.type === 'oauth2',
        children: data.scopesOptions || []
      }
    }
  }, {
    labelKey: 'common.label.description',
    formatter (data) {
      return <ElText type="info">{data}</ElText>
    },
    enabled: checkShowColumn(schemaEditModel.value?.securityParams, 'description'),
    prop: 'schema.description',
    type: 'common-form-label',
    minWidth: '250px'
  }])
})
</script>

<template>
  <el-container class="flex-column">
    <h3>
      {{ $t('api.label.authorization') }}
      <el-link
        v-if="editable"
        class="margin-left1"
        type="primary"
        @click="toEditSecuritySchemas"
      >
        <common-icon
          :size="18"
          :icon="hasSecurity?'Edit':'Plus'"
        />
      </el-link>
    </h3>
    <div v-if="hasSecurity">
      <template
        v-for="({schema:security}, index) in supportedSecurities"
        :key="index"
      >
        <el-text
          class="margin-right1"
          tag="b"
        >
          {{ $t('api.label.authType') }}:
        </el-text>
        <el-text type="primary">
          {{ security.name }}
        </el-text>
        <el-text
          v-if="security.in"
          type="info"
        >
          ({{ security.in }})
        </el-text>
        <el-text
          v-if="security.type"
          type="info"
        >
          &lt;{{ security.type }}&gt;
        </el-text>
        <el-text
          v-if="security.description"
          type="info"
        >
          {{ security.description }}
        </el-text>
        <pre />
      </template>
    </div>
    <el-container v-else>
      <el-text type="info">
        {{ $t('common.msg.noData') }}
      </el-text>
    </el-container>
    <common-window
      v-model="showSecurityConfigWindow"
      width="1000px"
      :title="$i18nKey('common.label.commonEdit', 'api.label.authorization')"
      :ok-click="saveSecuritySchemas"
    >
      <el-container class="flex-column">
        <common-table-form
          :model="schemaEditModel"
          :form-options="formOptions"
          data-list-key="securityParams"
          :show-operation="false"
        />
      </el-container>
    </common-window>
  </el-container>
</template>

<style scoped>

</style>
