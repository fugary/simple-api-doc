import { BASE_URL } from '@/config'
import { showCodeWindow } from '@/utils/DynamicUtils'
import { ref } from 'vue'
import { $i18nBundle } from '@/messages'
import { getPathUrl, isAdminUser } from '@/utils'

const dbUrl = getPathUrl(`${BASE_URL}${BASE_URL.endsWith('/') ? '' : '/'}h2-console`)
const editorContent = ref('')
export const ALL_MENUS = [
  {
    id: 1,
    iconCls: 'Files',
    nameCn: 'API管理',
    nameEn: 'API Management'
  },
  {
    id: 11,
    parentId: 1,
    iconCls: 'MessageBox',
    nameCn: 'API项目',
    nameEn: 'API Projects',
    menuUrl: '/api/projects'
  },
  {
    id: 13,
    parentId: 1,
    iconCls: 'Share',
    nameCn: '分享管理',
    nameEn: 'Share Management',
    menuUrl: '/admin/shares'
  },
  {
    id: 15,
    parentId: 1,
    iconCls: 'InputFilled',
    nameCn: '导入管理',
    nameEn: 'Import Management',
    menuUrl: '/admin/imports'
  },
  {
    id: 16,
    parentId: 1,
    iconCls: 'Grid',
    nameCn: '项目分组',
    nameEn: 'Project Groups',
    menuUrl: '/admin/groups'
  },
  {
    id: 5,
    iconCls: 'setting',
    nameCn: '系统管理',
    nameEn: 'System'
  },
  {
    id: 51,
    parentId: 5,
    iconCls: 'UserFilled',
    nameCn: '用户管理',
    nameEn: 'Users',
    menuUrl: '/admin/users'
  },
  {
    id: 52,
    parentId: 5,
    iconCls: 'Timer',
    nameCn: '任务管理',
    nameEn: 'Task Management',
    menuUrl: '/admin/simple-tasks',
    checkEnabled: isAdminUser
  },
  {
    id: 53,
    parentId: 5,
    iconCls: 'Document',
    nameCn: '日志管理',
    nameEn: 'Log Management',
    menuUrl: '/admin/logs',
    checkEnabled: isAdminUser
  },
  {
    id: 9,
    iconCls: 'BuildFilled',
    nameCn: '常用工具',
    nameEn: 'Tools'
  },
  {
    id: 91,
    parentId: 9,
    iconCls: 'InsertEmoticonOutlined',
    nameCn: '图标管理',
    nameEn: 'Icons',
    menuUrl: '/icons'
  },
  {
    id: 95,
    parentId: 9,
    iconCls: 'EditPen',
    nameCn: '代码编辑器',
    nameEn: 'Code Editor',
    click: () => {
      showCodeWindow(editorContent, {
        title: $i18nBundle('common.label.codeEdit'),
        fullEditor: true,
        readOnly: false,
        closeOnClickModal: false
      })
    }
  }, {
    id: 99,
    parentId: 9,
    iconCls: 'Coin',
    nameCn: '数据库管理',
    nameEn: 'Database',
    dbConsole: true,
    external: true,
    menuUrl: dbUrl,
    checkEnabled: isAdminUser
  }
]
