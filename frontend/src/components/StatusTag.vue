<template>
  <el-tag :type="tagTypeOf(value)" effect="light" :size="size">
    {{ labelOf(dict, value) }}
  </el-tag>
</template>

<script setup>
import { labelOf, tagTypeOf } from '../utils/dicts'

defineProps({
  value: {
    type: [String, Number],
    default: ''
  },
  dict: {
    type: Object,
    required: true
  },
  size: {
    type: String,
    default: 'small'
  }
})
</script>
