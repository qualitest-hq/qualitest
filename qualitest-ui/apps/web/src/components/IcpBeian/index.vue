<template>
  <span v-if="mps.number || number" class="beian-links">
    <a
      v-if="mps.number"
      class="icp-beian"
      :href="mps.url"
      target="_blank"
      rel="noopener noreferrer"
    ><img
      v-if="showIcon"
      class="mps-icon"
      src="/beian-mps.png"
      alt=""
      @error="showIcon = false"
    />{{ mps.number }}</a>
    <a
      v-if="number"
      class="icp-beian"
      href="https://beian.miit.gov.cn/"
      target="_blank"
      rel="noopener noreferrer"
    >{{ number }}</a>
  </span>
</template>

<script setup>
import { readIcpNumber, readMpsBeian } from "@/utils/icp"

const number = readIcpNumber()
const mps = readMpsBeian()
const showIcon = ref(!!mps.number)
</script>

<style scoped>
.beian-links {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.icp-beian {
  color: inherit;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.mps-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.icp-beian:hover {
  text-decoration: underline;
}
</style>
