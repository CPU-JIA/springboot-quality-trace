import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      onwarn(warning, warn) {
        const message = warning.message || ''
        const id = warning.id || ''
        if (
          id.includes('@vueuse/core') &&
          message.includes('contains an annotation that Rollup cannot interpret')
        ) {
          return
        }
        warn(warning)
      },
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          const normalizedId = id.replace(/\\/g, '/')
          if (normalizedId.includes('/node_modules/zrender/')) return 'zrender'
          if (normalizedId.includes('/node_modules/echarts/')) return 'echarts'
          if (
            normalizedId.includes('/node_modules/@popperjs/') ||
            normalizedId.includes('/node_modules/async-validator/') ||
            normalizedId.includes('/node_modules/dayjs/') ||
            normalizedId.includes('/node_modules/lodash') ||
            normalizedId.includes('/node_modules/memoize-one/')
          ) {
            return 'element-vendor'
          }
          if (normalizedId.includes('/node_modules/element-plus/')) return 'element'
          if (
            normalizedId.includes('/node_modules/vue/') ||
            normalizedId.includes('/node_modules/@vue/') ||
            normalizedId.includes('/node_modules/vue-router/') ||
            normalizedId.includes('/node_modules/pinia/')
          ) {
            return 'vue'
          }
          return 'vendor'
        }
      }
    }
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
