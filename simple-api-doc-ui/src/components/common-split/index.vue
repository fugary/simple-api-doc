<script setup>
import { onMounted, ref, useAttrs, shallowRef, watch, nextTick, computed } from 'vue'
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
  }
})
const elementSizesRefs = ref([])
const itemRefs = ref([])

const attrs = useAttrs()
const splitInstance = shallowRef()
const newSplitInstance = () => {
  splitInstance.value?.destroy()
  splitInstance.value = Split(itemRefs.value.map(itemRef => {
    const { width } = useElementSize(itemRef)
    elementSizesRefs.value.push(width)
    return itemRef
  }), {
    sizes: props.sizes,
    minSize: props.minSize,
    maxSize: props.maxSize,
    gutterAlign: props.gutterAlign,
    direction: props.direction,
    ...attrs
  })
}
onMounted(newSplitInstance)

watch(() => props.sizes, () => {
  nextTick(() => {
    newSplitInstance()
  })
})

const elementSizes = computed(() => elementSizesRefs.value?.map(sizeRef => sizeRef.value))

defineExpose({
  splitInstance,
  elementSizes
})

</script>

<template>
  <div class="common-split">
    <div
      v-for="(_, index) in sizes"
      ref="itemRefs"
      :key="index"
    >
      <slot :name="`split-${index}`" />
    </div>
  </div>
</template>

<style scoped>

</style>
