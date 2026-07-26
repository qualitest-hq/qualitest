<template>
  <div v-if="hasIssues" class="flow-validation-bar">
    <div
        v-for="(msg, i) in validation.errors"
        :key="'e-' + i"
        class="flow-validation-bar__item flow-validation-bar__item--error"
    >
      ✗ {{ msg }}
    </div>
    <div
        v-for="(msg, i) in validation.warnings"
        :key="'w-' + i"
        class="flow-validation-bar__item flow-validation-bar__item--warn"
    >
      ⚠ {{ msg }}
    </div>
    <div
        v-if="apiHealthMessages.length"
        class="flow-validation-bar__group-label"
    >
      API 语义
    </div>
    <div
        v-for="(msg, i) in apiHealthMessages"
        :key="'a-' + i"
        class="flow-validation-bar__item flow-validation-bar__item--warn"
    >
      ⚠ {{ msg }}
    </div>
  </div>
</template>

<script setup>
/**
 * 画布顶部校验条。
 * <p>
 * 上半：图结构错误/警告（缺开始节点、HTTP 未绑定、条件分支悬空等）。
 * 下半「API 语义」：当前页面图预检结果（绑定的 API 不存在、孤儿测值、抽取路径失效等）。
 * 两类都没有问题时整条隐藏。
 */
import { computed } from 'vue'

import { useFlowValidation } from '../composables/useFlowValidation'
import { useApiHealthStore } from '../stores/apiHealthStore'

const { validation } = useFlowValidation()
const apiHealth = useApiHealthStore()

/** API 语义告警文案列表 */
const apiHealthMessages = computed(() => apiHealth.messages)

/** 是否有任意结构问题或语义告警，决定整条是否显示 */
const hasIssues = computed(
  () =>
    validation.value.errors.length > 0
    || validation.value.warnings.length > 0
    || apiHealthMessages.value.length > 0,
)
</script>

<style scoped lang="scss">
.flow-validation-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 12;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.96);
  border-bottom: 1px solid var(--pd-divider);
  box-shadow: 0 2px 8px rgba(20, 60, 120, 0.06);
  max-height: 120px;
  overflow-y: auto;
  font-size: 11px;
  line-height: 1.45;
}

.flow-validation-bar__group-label {
  margin-top: 4px;
  font-size: 10px;
  font-weight: 600;
  color: #92400e;
  letter-spacing: 0.02em;
}

.flow-validation-bar__item--error {
  color: #b91c1c;
}

.flow-validation-bar__item--warn {
  color: #a16207;
}
</style>
