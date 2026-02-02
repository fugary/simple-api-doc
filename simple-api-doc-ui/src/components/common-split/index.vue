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
  }
})
const elementSizesRefs = ref([])
const itemRefs = ref([])
const isDragging = ref(false)

const attrs = useAttrs()
const splitInstance = shallowRef()
const newSplitInstance = () => {
  if (splitInstance.value) {
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
    onDragStart: (sizes) => {
      isDragging.value = true
      if (attrs.onDragStart) {
        attrs.onDragStart(sizes)
      }
    },
    onDragEnd: (sizes) => {
      isDragging.value = false
      if (attrs.onDragEnd) {
        attrs.onDragEnd(sizes)
      }
    }
  })
}

onMounted(() => {
  newSplitInstance()
})

watch(() => props.sizes, (newSizes) => {
  if (splitInstance.value) {
    splitInstance.value.setSizes(newSizes)
  }
})

watch(() => props.disabled, () => {
  newSplitInstance()
})

const elementSizes = computed(() => elementSizesRefs.value?.map(sizeRef => sizeRef.value))

defineExpose({
  splitInstance,
  elementSizes
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
}
/* Highlight when dragging (controlled by JS state) */
.is-dragging > :deep(.gutter) {
  background-color: #409eff !important;
}
</style>
