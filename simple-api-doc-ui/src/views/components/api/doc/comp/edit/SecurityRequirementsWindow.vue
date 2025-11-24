<script setup lang="jsx">
import { computed, ref } from 'vue'
import { cloneDeep } from 'lodash-es'
import { $i18nKey } from '@/messages'
import {
  checkAndSaveDocInfoDetail,
  getOauth2ConfigInfo,
  newDocInfoDetail,
  securitySchemas2List
} from '@/services/api/ApiDocEditService'
import { defineFormOptions, defineTableButtons } from '@/components/utils'
import { checkShowColumn } from '@/utils'
import { ElText } from 'element-plus'

const props = defineProps({
  apiDocDetail: {
    type: Object,
    default: null
  },
  projectInfoDetail: {
    type: Object,
    required: true
  },
  docSecurity: {
    type: Boolean,
    default: true
  }
})
const securitySchemaList = computed(() => {
  const securitySchemas = JSON.parse(props.projectInfoDetail?.securitySchemas?.schemaContent || '{}')
  return securitySchemas2List(securitySchemas)
})
// 支持的
const getSecurityRequirements = (create) => {
  let securityRequirements = props.docSecurity ? props.apiDocDetail?.securityRequirements : props.projectInfoDetail?.securityRequirements
  if (!securityRequirements && create) {
    if (props.docSecurity) {
      securityRequirements = {
        ...newDocInfoDetail(props.apiDocDetail),
        bodyType: 'doc_security_requirements'
      }
    } else {
      securityRequirements = {
        infoId: props.projectInfoDetail?.id,
        projectId: props.projectInfoDetail?.projectId,
        bodyType: 'security_requirements'
      }
    }
  }
  return securityRequirements
}
const securityRequirements = computed(() => {
  const securityRequirements = getSecurityRequirements()
  return JSON.parse(securityRequirements?.schemaContent || '[]')
})
const supportedKeys = computed(() => securityRequirements.value.flatMap(config => Object.keys(config)))
const showSecurityConfigWindow = ref(false)
const schemaEditModel = ref({
  securityParams: []
})
const emit = defineEmits(['schemaUpdated', 'toEditSecuritySchemas'])
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
  const securityRequirements = getSecurityRequirements(true)
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
const buttons = computed(() => {
  return defineTableButtons([{
    type: 'success',
    label: $i18nKey('common.label.commonEdit', 'api.label.authDetails'),
    enabled: props.docSecurity,
    click () {
      emit('toEditSecuritySchemas')
      showSecurityConfigWindow.value = false
    }
  }])
})
defineExpose({
  toEditSecuritySchemas
})
</script>

<template>
  <common-window
    v-model="showSecurityConfigWindow"
    width="1000px"
    :title="$i18nKey('common.label.commonEdit', 'api.label.authorization')"
    :ok-click="saveSecuritySchemas"
    :buttons="buttons"
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
</template>

<style scoped>

</style>
