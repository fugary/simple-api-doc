import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import ShareDoc from '@/views/share/ShareDoc.vue'
import UserRoutes from '@/route/UserRoutes'
import SimpleAdminRoutes from '@/route/SimpleAdminRoutes'
import ApiRoutes from '@/route/ApiRoutes'
import ToolsRoutes from '@/route/ToolsRoutes'
import { checkRouteAuthority, processRouteLoading } from '@/authority'
import { checkReplaceHistoryShouldReplace } from '@/route/RouteUtils'
import { useTabsViewStore } from '@/stores/TabsViewStore'
import { closeAllOnRouteChange } from '@/utils/DynamicUtils'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/HomeView.vue'),
      meta: {
        icon: 'HomeFilled',
        labelKey: 'common.label.index'
      },
      children: [{
        path: '',
        name: 'index',
        component: () => import('@/views/Index.vue'),
        meta: {
          icon: 'HomeFilled',
          labelKey: 'common.label.index'
        },
        redirect: '/api/projects'
      }, {
        path: 'about',
        name: 'about',
        component: () => import('@/views/account/AboutView.vue')
      }, {
        path: 'personal',
        name: 'personal',
        component: () => import('@/views/admin/UserEdit.vue'),
        props: {
          personal: true
        }
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/404.vue'),
        meta: {
          icon: 'QuestionFilled',
          label: 'Not Found'
        }
      },
      ...UserRoutes,
      ...SimpleAdminRoutes,
      ...ApiRoutes,
      ...ToolsRoutes
      ]
    }, {
      path: '/login',
      name: 'Login',
      component: Login,
      meta: {
        beforeLogin: true
      }
    }, {
      path: '/share/:shareId',
      name: 'ShareDoc',
      component: ShareDoc,
      meta: {
        beforeLogin: true
      }
    }
  ]
})

const scrollMain = (to, scrollOption) => {
  setTimeout(() => { // 因为有0.3s动画需要延迟到动画之后
    scrollOption = scrollOption || to?.meta?.scroll || { top: 0 }
    document.querySelector('.home-main')?.scrollTo(scrollOption)
  }, 350)
}

/**
 * 自定义路由滚动行为，在home-main容器中滚动顶部
 * @param to
 * @param from
 */
export const routeScrollBehavior = (to, from) => {
  const tabsViewStore = useTabsViewStore()
  const scrollOption = !checkReplaceHistoryShouldReplace(to, from) ? tabsViewStore.currentTabItem?.scroll : undefined
  scrollMain(to, scrollOption)
}

router.beforeEach(() => {
  document.querySelectorAll('.common-el-tooltip,.common-el-popover')
    .forEach(el => el.remove()) // 清理所有残留 Tooltip
})
router.beforeEach(checkRouteAuthority)
router.afterEach((...args) => {
  processRouteLoading(...args)
  routeScrollBehavior(...args)
  closeAllOnRouteChange()
})

export default router
