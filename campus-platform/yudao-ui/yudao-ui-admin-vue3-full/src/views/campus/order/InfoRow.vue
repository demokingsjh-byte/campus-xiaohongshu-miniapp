<template>
  <div class="info-row">
    <span>{{ label }}</span>
    <div>
      <strong>{{ value }}</strong>
      <button v-if="copyable && value !== '-'" class="copy-link" type="button" @click="copy">
        复制
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  label: string
  value: string
  copyable?: boolean
}>()

const message = useMessage()
const copy = async () => {
  if (!props.value || props.value === '-') return
  await navigator.clipboard.writeText(props.value)
  message.success('已复制')
}
</script>
