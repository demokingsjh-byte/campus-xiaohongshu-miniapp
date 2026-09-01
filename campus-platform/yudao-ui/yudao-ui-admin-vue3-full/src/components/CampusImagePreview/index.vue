<template>
  <div v-if="urls.length" class="campus-image-preview">
    <el-image
      :src="urls[0]"
      :preview-src-list="urls"
      :preview-teleported="true"
      fit="cover"
      class="campus-image-preview__image"
      :style="imageStyle"
    >
      <template #error>
        <div class="campus-image-preview__empty"><Icon icon="ep:picture" :size="20" /></div>
      </template>
    </el-image>
    <span v-if="urls.length > 1" class="campus-image-preview__count">+{{ urls.length - 1 }}</span>
  </div>
  <span v-else class="campus-image-preview__none">暂无图片</span>
</template>

<script setup lang="ts">
import { resolveCampusMediaUrl } from '@/utils/campusMedia'

defineOptions({ name: 'CampusImagePreview' })

const props = withDefaults(defineProps<{ value?: unknown; size?: number }>(), { size: 48 })

const normalizeUrls = (value: unknown): string[] => {
  if (!value) return []
  if (Array.isArray(value)) return value.flatMap(normalizeUrls)
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    return normalizeUrls(record.url || record.path || record.src)
  }
  if (typeof value !== 'string') return []
  const text = value.trim()
  if (!text || text === '[]' || text === '{}') return []
  if (text.startsWith('[') || text.startsWith('{')) {
    try {
      return normalizeUrls(JSON.parse(text))
    } catch {
      // Fall through for legacy plain-text values.
    }
  }
  return text
    .split(',')
    .map((item) => item.trim())
    .filter((item) => /^(https?:\/\/|\/)/i.test(item))
    .map(resolveCampusMediaUrl)
}

const urls = computed(() => Array.from(new Set(normalizeUrls(props.value))))
const imageStyle = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
</script>

<style scoped>
.campus-image-preview {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

.campus-image-preview__image {
  overflow: hidden;
  cursor: zoom-in;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.campus-image-preview__empty {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
}

.campus-image-preview__count {
  position: absolute;
  right: -7px;
  bottom: -5px;
  min-width: 20px;
  padding: 1px 5px;
  border: 2px solid var(--el-bg-color);
  border-radius: 10px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 11px;
  line-height: 16px;
}

.campus-image-preview__none {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
