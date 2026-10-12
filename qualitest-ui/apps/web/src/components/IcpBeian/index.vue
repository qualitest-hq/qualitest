<template>
  <a
    v-if="number"
    class="icp-beian"
    href="https://beian.miit.gov.cn/"
    target="_blank"
    rel="noopener noreferrer"
  >{{ number }}</a>
</template>

<script setup>
import { readIcpNumber } from "@/utils/icp"

const number = readIcpNumber()
</script>

<style scoped>
.icp-beian {
  color: inherit;
  text-decoration: none;
}

.icp-beian:hover {
  text-decoration: underline;
}
</style>
