<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeAndLocaleMenus } from '@/services/menu/MenuService'
import { useLoginConfigStore } from '@/stores/LoginConfigStore'
import { ElMessage } from 'element-plus'
import { useCopyRight } from '@/services/api/ApiCommonService'
import { Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const loginConfigStore = useLoginConfigStore()

const themeAndLocaleMenus = ref(useThemeAndLocaleMenus())

/**
 * @type {[CommonFormOption]}
 */
const loginFormOptions = [{
  labelKey: 'common.label.username',
  required: true,
  prop: 'userName',
  attrs: {
    size: 'large',
    'prefix-icon': User
  }
}, {
  labelKey: 'common.label.password',
  required: true,
  prop: 'userPassword',
  attrs: {
    size: 'large',
    showPassword: true,
    'prefix-icon': Lock
  }
}]

/**
 * @type {LoginVo}
 */
const loginVo = ref({
  userName: loginConfigStore.lastLoginName || 'admin',
  userPassword: ''
})

const loading = ref(false)

const submitForm = async () => {
  // If called from button click, it might pass the event or undefined if we don't pass args
  // If called from @submit-form, it passes the form instance

  // We prefer using the ref if available to be consistent
  const form = formRef.value?.form
  if (!form) return

  await form.validate(async (valid) => {
    if (valid) {
      loading.value = true
      const loginResult = await loginConfigStore.login(loginVo.value)
        .finally(() => {
          loading.value = false
        })
      if (loginResult.success) {
        router.push('/')
      } else {
        ElMessage.error(loginResult.message)
      }
    }
  })
}
const formRef = ref()
const copyRight = useCopyRight()
</script>

<template>
  <div class="login-container">
    <!-- Tools -->
    <div class="login-tools">
      <common-menu
        :menus="themeAndLocaleMenus"
        mode="horizontal"
        :ellipsis="false"
      />
    </div>

    <!-- Left Branding -->
    <div class="login-branding">
      <div class="branding-decoration" />
      <div class="branding-content">
        <h1 class="branding-title">
          {{ $t('common.label.title') }}
        </h1>
        <p class="branding-subtitle">
          {{ $t('common.msg.loginSubtitle') }}
        </p>
      </div>
      <div class="branding-footer">
        <copy-right />
      </div>
    </div>

    <!-- Right Form Section -->
    <div class="login-form-section">
      <div class="form-wrapper">
        <div class="form-header">
          <h2>{{ $t('common.msg.loginTitle') }}</h2>
          <p class="form-subtitle">
            {{ $t('common.msg.loginWelcome') }}
          </p>
        </div>

        <common-form
          ref="formRef"
          :model="loginVo"
          :options="loginFormOptions"
          class="modern-form"
          label-position="top"
          :show-buttons="false"
          @submit-form="submitForm"
        />

        <div class="form-actions">
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="submitForm"
          >
            {{ loading ? $t('common.label.logining') : $t('common.label.login') }}
          </el-button>

          <el-button
            class="reset-btn"
            text
            @click="formRef.form.resetFields()"
          >
            {{ $t('common.label.reset') }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
@import '@/assets/login.css';
</style>
