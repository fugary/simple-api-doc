<script setup>
import { onMounted, ref, useAttrs, shallowRef, watch, computed } from 'vue'
import Split from 'split.js'
import { useElementSize } from '@vueuse/core'

/**
 * 更多属性配置可以参考文档
 * @link https://github.com/nathancahill/split/tree/master/packages/splitjs <br>
 */
const props = defineProps({
  sizes: {
    type: Array,
    default: () => [25, 75]
  },
  minSize: {
    type: [Number, Array],
    default: 100
  },
  maxSize: {
    type: [Number, Array],
    default: Infinity
  },
  direction: {
    type: String,
    default: 'horizontal',
    validator (value) {
      return ['horizontal', 'vertical'].includes(value)
    }
  },
  gutterAlign: {
    type: String,
    default: 'center',
    validator (value) {
      return ['start', 'center', 'end'].includes(value)
    }
  },
  disabled: {
    type: Boolean,
    default: false
  },
  collapsible: {
    type: Boolean,
    default: false
  },
  triggerTop: {
    type: [Number, String],
    default: 100
  }
})

const emit = defineEmits(['update:sizes', 'collapse', 'drag', 'drag-start', 'drag-end'])

const elementSizesRefs = ref([])
const itemRefs = ref([])
const isDragging = ref(false)
const firstGutterEl = shallowRef(null)

const canCollapse = computed(() => props.collapsible && props.direction === 'horizontal')
const isCollapsed = ref(props.sizes?.[0] <= 3)
const savedSize = ref(props.sizes?.[0] > 3 ? props.sizes[0] : 20)

const computedTriggerTop = computed(() => {
  if (typeof props.triggerTop === 'number') {
    return `${props.triggerTop}px`
  }
  return props.triggerTop || '100px'
})

const syncCollapseState = (firstSize) => {
  if (firstSize === undefined) return
  if (firstSize <= 3) {
    isCollapsed.value = true
  } else {
    isCollapsed.value = false
    savedSize.value = firstSize
  }
}

const calcProportionalSizes = (baseSizes, firstPaneSize) => {
  const remainingPanes = baseSizes.slice(1)
  const remainingSum = remainingPanes.reduce((a, b) => a + b, 0) || 1
  return [firstPaneSize, ...remainingPanes.map(s => (s / remainingSum) * (100 - firstPaneSize))]
}

const toggleCollapse = (collapse) => {
  const shouldCollapse = collapse ?? !isCollapsed.value
  const currentSizes = [...props.sizes]
  if (shouldCollapse) {
    if (currentSizes[0] > 3) {
      savedSize.value = currentSizes[0]
    }
    const newSizes = calcProportionalSizes(currentSizes, 0)
    isCollapsed.value = true
    emit('update:sizes', newSizes)
    emit('collapse', true)
    splitInstance.value?.setSizes(newSizes)
  } else {
    const targetSize = savedSize.value > 3 ? savedSize.value : 20
    const newSizes = calcProportionalSizes(currentSizes, targetSize)
    isCollapsed.value = false
    emit('update:sizes', newSizes)
    emit('collapse', false)
    splitInstance.value?.setSizes(newSizes)
  }
}

const attrs = useAttrs()
const splitInstance = shallowRef()
const newSplitInstance = () => {
  if (splitInstance.value) {
    firstGutterEl.value = null
    splitInstance.value.destroy()
    splitInstance.value = null
  }
  // Clear previous size refs to avoid duplicates/leaks on re-init
  elementSizesRefs.value = []

  if (props.disabled) return

  const elements = itemRefs.value.filter(el => el)
  if (elements.length === 0) return

  splitInstance.value = Split(elements.map(itemRef => {
    const { width, height } = useElementSize(itemRef)
    elementSizesRefs.value.push(props.direction === 'vertical' ? height : width)
    return itemRef
  }), {
    sizes: props.sizes,
    minSize: props.minSize,
    maxSize: props.maxSize,
    gutterAlign: props.gutterAlign,
    gutterSize: 5,
    direction: props.direction,
    ...attrs,
    gutter: (index, direction) => {
      const gutter = document.createElement('div')
      gutter.className = `gutter gutter-${direction}`
      gutter.addEventListener('mousedown', () => {
        gutter.classList.add('is-active')
      })
      return gutter
    },
    onDragStart: (sizes) => {
      isDragging.value = true
      if (attrs.onDragStart) {
        attrs.onDragStart(sizes)
      }
      emit('drag-start', sizes)
    },
    onDrag: (sizes) => {
      if (canCollapse.value) {
        syncCollapseState(sizes?.[0])
      }
      if (attrs.onDrag) {
        attrs.onDrag(sizes)
      }
      emit('drag', sizes)
    },
    onDragEnd: (sizes) => {
      isDragging.value = false
      // remove is-active from all gutters
      const container = itemRefs.value[0]?.parentNode
      if (container) {
        container.querySelectorAll('.gutter.is-active').forEach(el => el.classList.remove('is-active'))
      }

      if (canCollapse.value && sizes?.[0] !== undefined) {
        if (sizes[0] <= 3) {
          isCollapsed.value = true
          const newSizes = calcProportionalSizes(sizes, 0)
          splitInstance.value?.setSizes(newSizes)
          emit('update:sizes', newSizes)
          emit('collapse', true)
        } else {
          isCollapsed.value = false
          savedSize.value = sizes[0]
          emit('update:sizes', sizes)
          emit('collapse', false)
        }
      }

      if (attrs.onDragEnd) {
        attrs.onDragEnd(sizes)
      }
      emit('drag-end', sizes)
    }
  })

  if (canCollapse.value) {
    const container = itemRefs.value[0]?.parentNode
    const gutter = container?.querySelector('.gutter.gutter-horizontal')
    if (gutter) {
      firstGutterEl.value = gutter
    }
  }
}

onMounted(() => {
  newSplitInstance()
})

watch(() => props.sizes, (newSizes, oldSizes) => {
  syncCollapseState(newSizes?.[0])
  if (splitInstance.value && newSizes?.length === oldSizes?.length) {
    try {
      splitInstance.value.setSizes(newSizes)
      return
    } catch (e) {
      console.debug('Split setSizes error, recreating instance:', e)
    }
  }
  newSplitInstance()
}, { flush: 'post' })

watch(() => props.disabled, () => {
  newSplitInstance()
}, { flush: 'post' })

const elementSizes = computed(() => elementSizesRefs.value?.map(sizeRef => sizeRef.value))

defineExpose({
  splitInstance,
  elementSizes,
  toggleCollapse,
  isCollapsed
})
</script>

<template>
  <div
    class="common-split"
    :class="{ 'is-disabled': disabled, 'is-dragging': isDragging }"
  >
    <div
      v-for="(_, index) in sizes"
      ref="itemRefs"
      :key="index"
      class="split-pane"
    >
      <slot :name="`split-${index}`" />
    </div>
    <teleport
      v-if="canCollapse && firstGutterEl"
      :to="firstGutterEl"
    >
      <div
        class="split-collapse-trigger"
        :class="{ 'is-collapsed': isCollapsed }"
        :style="{ top: computedTriggerTop }"
        @mousedown.stop
        @click.stop="toggleCollapse()"
      >
        <slot
          name="collapse-trigger"
          :is-collapsed="isCollapsed"
          :toggle="toggleCollapse"
        >
          <common-icon
            :icon="isCollapsed ? 'DArrowRight' : 'DArrowLeft'"
            :size="12"
          />
        </slot>
      </div>
    </teleport>
  </div>
</template>

<style scoped>
.common-split {
  height: 100%;
  width: 100%;
}
.split-pane {
  overflow: hidden;
  height: 100%;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.common-split.is-dragging .split-pane {
  transition: none !important;
}
.common-split.is-disabled {
  display: flex;
  flex-direction: row;
}
.common-split.is-disabled > .split-pane:first-child {
  width: auto !important;
  flex: none;
}
.common-split.is-disabled > .split-pane:last-child {
  flex: 1;
  width: auto !important;
}
:deep(.gutter) {
  background-color: #eee;
  background-repeat: no-repeat;
  background-position: 50%;
  position: relative;
  overflow: visible;
  z-index: 10;
}
/* Highlight when dragging (controlled by JS state) */
:deep(.gutter.is-active) {
  background-color: #409eff !important;
}

:deep(.split-collapse-trigger) {
  position: absolute;
  width: 14px;
  padding: 10px 0;
  background-color: var(--el-bg-color-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer !important;
  color: var(--el-text-color-secondary);
  user-select: none;
  transition: width 0.2s ease, background-color 0.2s ease, color 0.2s ease;
  z-index: 11;
}

/* 展开把手 (树收起时，把手在拖拽条右侧，点击向右展开) */
:deep(.split-collapse-trigger.is-collapsed) {
  left: 100%;
  right: auto;
  border-radius: 0 6px 6px 0;
  border: 1px solid var(--el-border-color-lighter);
  border-left: none;
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.08);
}

/* 收起把手 (树展开时，把手在拖拽条左侧，点击向左收起) */
:deep(.split-collapse-trigger:not(.is-collapsed)) {
  right: 100%;
  left: auto;
  border-radius: 6px 0 0 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-right: none;
  box-shadow: -2px 0 6px rgba(0, 0, 0, 0.08);
}

:deep(.split-collapse-trigger:hover) {
  width: 18px;
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}
</style>
