<script setup>
import { computed, ref, watch } from 'vue'
import { $i18nBundle, $i18nConcat, $i18nKey } from '@/messages'
import { processProjectInfos, processSecuritySchema, securitySchemas2List } from '@/services/api/ApiDocEditService'
import { defineFormOptions, defineTableColumns } from '@/components/utils'
import CommonIcon from '@/components/common-icon/index.vue'
import { ElButton } from 'element-plus'
import SimpleEditWindow from '@/views/components/utils/SimpleEditWindow.vue'
import { cloneDeep } from 'lodash-es'
import { $coreConfirm, getSingleSelectOptions } from '@/utils'
import { SECURITY_IN_TYPES, SECURITY_OAUTH2_AUTH_TYPES, SECURITY_TYPE_TYPES } from '@/consts/ApiConstants'
import ApiProjectInfoDetailApi from '@/api/ApiProjectInfoDetailApi'
import { toEditSecuritySchemas } from '@/utils/DynamicUtils'
import { calcSecurityRequirements } from '@/services/api/ApiDocPreviewService'

const showWindow = defineModel({
  type: Boolean,
  default: false
})
const projectItem = defineModel('projectItem', {
  type: Object,
  default: null
})
const infoListDetails = ref([])
const currentInfoId = ref()
const initProjectItem = () => {
  console.log('=====================change', projectItem.value.infoList)
  infoListDetails.value = processProjectInfos(projectItem.value)
  infoListDetails.value.forEach(infoDetail => {
    if (infoDetail.securitySchemas) {
      const securitySchema = infoDetail.securitySchemas
      infoDetail.securitySchema = securitySchema
      const schemaContentObj = infoDetail.securitySchema.schemaContentObj = JSON.parse(securitySchema.schemaContent || '{}')
      infoDetail.schemaList = securitySchemas2List(schemaContentObj, securitySchema.id)
    } else {
      infoDetail.securitySchema = {
        schemaContentObj: {},
        schemaContent: '{}',
        projectId: projectItem.value.id,
        infoId: infoDetail.id,
        bodyType: 'security'
      }
    }
  })
  currentInfoId.value = infoListDetails.value?.[0]?.id
}
watch(projectItem, () => initProjectItem(), { immediate: true })
const columns = computed(() => {
  return defineTableColumns([{
    labelKey: 'common.label.name',
    prop: 'schemaName',
    minWidth: '100px'
  }, {
    labelKey: 'api.label.type',
    prop: 'schema.type',
    minWidth: '100px'
  }, {
    labelKey: 'api.label.paramIn',
    prop: 'schema.in',
    minWidth: '100px'
  }, {
    labelKey: 'common.label.description',
    prop: 'schema.description',
    minWidth: '250px'
  }])
})
const tableButtons = computed(() => {
  return [{
    labelKey: 'common.label.edit',
    type: 'primary',
    click: (item) => {
      newOrEdit(item.schemaName)
    }
  }, {
    labelKey: 'common.label.delete',
    type: 'danger',
    click: item => {
      $coreConfirm($i18nBundle('common.msg.deleteConfirm'))
        .then(() => saveOrDeleteSecuritySchema(item, true))
    }
  }]
})
// ============ 编辑 ============
const showEditWindow = ref(false)
const securityInfoModel = ref()
const getOauth2Options = () => {
  const oauth2 = securityInfoModel.value?.schema?.type === 'oauth2'
  const oauth2Type = securityInfoModel.value?.oauth2Type
  const properties = ['authorizationUrl', 'tokenUrl', 'refreshUrl', 'scopes']
  return oauth2 && oauth2Type
    ? properties.map(label => {
      const pattern = label?.toLowerCase()?.includes('url') ? /https?:\/\/.*/ : undefined
      const type = label === 'scopes' ? 'common-object' : 'input'
      return {
        label,
        prop: `schema.flows.${oauth2Type}.${label}`,
        type,
        pattern
      }
    })
    : []
}
const editFormOptions = computed(() => {
  const oauth2 = securityInfoModel.value?.schema?.type === 'oauth2'
  const needIn = ['http', 'apiKey'].includes(securityInfoModel.value?.schema?.type)
  return defineFormOptions([{
    labelKey: 'common.label.name',
    prop: 'schemaName',
    required: true
  }, {
    labelKey: 'api.label.type',
    prop: 'schema.type',
    type: 'select',
    children: getSingleSelectOptions(...SECURITY_TYPE_TYPES),
    attrs: {
      clearable: false
    },
    required: true
  }, {
    labelKey: 'api.label.paramIn',
    prop: 'schema.in',
    type: 'select',
    children: getSingleSelectOptions(...SECURITY_IN_TYPES),
    attrs: {
      clearable: false
    },
    required: needIn
  }, {
    labelKey: 'api.label.authParamName',
    prop: 'schema.name',
    required: needIn
  }, {
    label: 'scheme',
    prop: 'schema.scheme'
  }, {
    label: 'bearerFormat',
    prop: 'schema.bearerFormat'
  }, {
    label: 'openIdConnectUrl',
    prop: 'schema.openIdConnectUrl',
    enabled: securityInfoModel.value?.schema?.type === 'openIdConnect',
    pattern: /https?:\/\/.*/,
    required: true
  }, {
    label: 'OAuth2 Type',
    prop: 'oauth2Type',
    type: 'select',
    required: true,
    enabled: oauth2,
    children: getSingleSelectOptions(...SECURITY_OAUTH2_AUTH_TYPES)
  }, ...getOauth2Options(), {
    labelKey: 'common.label.description',
    prop: 'schema.description'
  }])
})
const newOrEdit = (name) => {
  const currentInfoDetail = infoListDetails.value.find(info => currentInfoId.value === info.id)
  if (name) {
    securityInfoModel.value = cloneDeep(currentInfoDetail.schemaList?.find(schema => schema.schemaName === name))
  } else {
    securityInfoModel.value = {
      schema: {
        type: 'apiKey',
        in: 'header',
        name: 'Authorization'
      }
    }
  }
  showEditWindow.value = true
}
const emit = defineEmits(['saveSecuritySchema'])
const saveOrDeleteSecuritySchema = async (item, isDelete) => {
  const currentDetail = infoListDetails.value.find(info => currentInfoId.value === info.id)
  const securitySchema = cloneDeep(currentDetail.securitySchema)
  const schemaContentObj = securitySchema.schemaContentObj
  let { schemaName, schema } = item
  if (isDelete) {
    delete schemaContentObj[schemaName]
  } else {
    schema = processSecuritySchema(securityInfoModel.value, schema)
    console.log('=======================schema', schema)
    schemaContentObj[schemaName] = schema
  }
  securitySchema.schemaContent = JSON.stringify(schemaContentObj)
  delete securitySchema.schemaContentObj
  return ApiProjectInfoDetailApi.saveOrUpdate(securitySchema)
    .then((resultData) => emit('saveSecuritySchema', resultData))
}
const saveSecuritySchema = async () => {
  return saveOrDeleteSecuritySchema(securityInfoModel.value)
}
const toEditDefaultSecuritySchemas = projectInfoDetail => toEditSecuritySchemas({
  projectInfoDetail,
  docSecurity: false,
  onSchemaUpdated: (...args) => emit('saveSecuritySchema', ...args)
})
</script>

<template>
  <common-window
    v-model="showWindow"
    width="1000px"
    :title="$i18nKey('common.label.commonEdit', 'api.label.authDetails')"
    :ok-label="$t('common.label.close')"
    :show-cancel="false"
  >
    <el-container class="flex-column">
      <el-tabs v-model="currentInfoId">
        <el-tab-pane
          v-for="(infoDetail, index) in infoListDetails"
          :key="index"
          :label="$i18nConcat(infoDetail.folderName, $t('api.label.authorization'))"
          :name="infoDetail.id"
        >
          <common-table
            :columns="columns"
            :data="infoDetail.schemaList"
            :buttons="tableButtons"
            :buttons-column-attrs="{minWidth:'150px'}"
          />
          <el-container class="margin-top2">
            <el-button
              type="primary"
              size="small"
              @click="newOrEdit()"
            >
              <common-icon
                class="margin-right1"
                icon="Plus"
              />
              {{ $i18nKey('common.label.commonAdd1', 'api.label.authorization') }}
            </el-button>
            <el-badge
              :value="calcSecurityRequirements(infoDetail)?.length"
              :show-zero="false"
              type="primary"
              class="padding-left2"
            >
              <el-button
                type="success"
                size="small"
                @click="toEditDefaultSecuritySchemas(infoDetail)"
              >
                <common-icon
                  class="margin-right1"
                  icon="Lock"
                />
                {{ $t('api.label.defaultAuth') }}
              </el-button>
            </el-badge>
          </el-container>
        </el-tab-pane>
      </el-tabs>
      <simple-edit-window
        v-model="securityInfoModel"
        v-model:show-edit-window="showEditWindow"
        :form-options="editFormOptions"
        :name="$t('api.label.authorization')"
        :save-current-item="saveSecuritySchema"
        label-width="130px"
      />
    </el-container>
  </common-window>
</template>

<style scoped>

</style>
