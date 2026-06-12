<script setup>
import { computed } from 'vue'
import { getAuthorityCode, getAuthorityTooltip, getGroupUserAuthorities } from '@/hooks/ApiProjectGroupHooks'

const props = defineProps({
  userGroups: {
    type: Array,
    default: () => []
  },
  users: {
    type: Array,
    default: () => []
  }
})

const groupUsers = computed(() => getGroupUserAuthorities(props.userGroups, props.users))
</script>

<template>
  <span
    v-if="groupUsers.length"
    class="group-authority-list"
  >
    <template
      v-for="({apiUser, authorities}, index) in groupUsers"
      :key="apiUser.id"
    >
      <span class="group-authority-inline">
        <span class="group-authority-inline__name">{{ apiUser.userName }}</span>
        <el-tag
          v-common-tooltip="getAuthorityTooltip(authorities)"
          size="small"
          type="primary"
          effect="plain"
          class="group-authority-inline__tag"
        >
          {{ getAuthorityCode(authorities) }}
        </el-tag>
      </span>
      <span
        v-if="index < groupUsers.length - 1"
        class="group-authority-inline__separator"
      >, </span>
    </template>
  </span>
</template>

<style scoped>
.group-authority-list {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  overflow: hidden;
  line-height: 22px;
  vertical-align: middle;
}

.group-authority-inline {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  vertical-align: middle;
}

.group-authority-inline__name {
  display: inline-block;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.group-authority-inline__tag {
  margin-left: 3px;
  cursor: pointer;
  vertical-align: middle;
}

.group-authority-inline__tag:deep(.el-tag__content) {
  line-height: 16px;
}

.group-authority-inline__tag.el-tag--small {
  height: 18px;
  padding: 0 5px;
  background: transparent;
  border-color: var(--el-color-primary);
}

.group-authority-inline__separator {
  color: var(--el-text-color-secondary);
  margin-right: 4px;
}
</style>
