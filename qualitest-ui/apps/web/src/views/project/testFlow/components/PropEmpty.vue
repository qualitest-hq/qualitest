<template>
  <div class="prop-empty">
    <div v-if="icon" class="prop-empty__icon">{{ icon }}</div>
    <slot />
  </div>
</template>

<script setup>
/** 右栏空态：与属性 / 运行配置面板共用 */
defineProps({
  icon: {
    type: String,
    default: '⚙',
  },
});
</script>

<style scoped lang="scss">
@use '../styles/propPanel.scss' as prop;

.prop-empty {
  @include prop.flow-prop-empty;

  &__icon {
    @include prop.flow-prop-empty-icon;
  }
}
</style>
