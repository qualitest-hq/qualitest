<template>
  <footer v-if="visible || icpNumber" class="copyright" :class="{ 'is-beian-only': !visible }">
    <icp-beian />
    <span v-if="visible">{{ content }}</span>
  </footer>
</template>

<script setup>
import IcpBeian from '@/components/IcpBeian/index.vue'
import { readIcpNumber } from '@/utils/icp'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()

const visible = computed(() => settingsStore.footerVisible)
const content = computed(() => settingsStore.footerContent)
const icpNumber = readIcpNumber()
</script>

<style scoped>
.copyright {
  flex-shrink: 0;
  height: 36px;
  padding: 10px 20px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
  background-color: #f8f8f8;
  color: #666;
  font-size: 14px;
  border-top: 1px solid #e7e7e7;
  box-sizing: border-box;
}

.copyright.is-beian-only {
  justify-content: center;
}
</style>