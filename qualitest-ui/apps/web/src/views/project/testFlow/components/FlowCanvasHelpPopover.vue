<template>
  <div class="help-wrap">
    <button
        :aria-expanded="isOpen"
        :class="{ 'is-open': isOpen }"
        aria-controls="flowHelpPopover"
        class="btn btn--help"
        title="快捷键帮助"
        type="button"
        @click.stop="toggle"
    >
      ?
    </button>
    <div
        v-if="isOpen"
        id="flowHelpPopover"
        aria-label="快捷键帮助"
        class="help-popover is-open"
        role="dialog"
    >
      <div class="help-popover__title">快捷键</div>
      <div class="help-popover__list">
        <div class="help-popover__item">
          <kbd>Del</kbd><span>删除选中节点或边</span>
        </div>
        <div class="help-popover__item">
          <kbd>Ctrl+Z</kbd><span>撤销上一步操作</span>
        </div>
        <div class="help-popover__item">
          <kbd>Esc</kbd><span>停止路径模拟 / 运行回放 / 场景运行</span>
        </div>
        <div class="help-popover__item">
          <kbd>←</kbd><kbd>→</kbd><span>模拟或回放的上一步 / 下一步</span>
        </div>
        <div class="help-popover__item">
          <kbd>滚轮</kbd><span>缩放画布</span>
        </div>
        <div class="help-popover__item">
          <kbd>左键拖</kbd><span>在空白处平移画布</span>
        </div>
        <div class="help-popover__item">
          <kbd>中键拖</kbd><span>平移画布</span>
        </div>
        <div class="help-popover__item">
          <kbd>Shift+拖</kbd><span>框选节点</span>
        </div>
        <div class="help-popover__item">
          <span class="help-popover__label">连线</span><span>输出锚点 → 输入锚点</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 顶栏快捷键帮助弹出层。
 * 支持 v-model:open 供键盘 Esc 关闭；点击按钮切换显示。
 */
import { computed, onBeforeUnmount, onMounted } from 'vue';

const props = defineProps({
  open: { type: Boolean, default: false },
});

const emit = defineEmits(['update:open']);

const isOpen = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val),
});

function toggle() {
  isOpen.value = !isOpen.value;
}

/** 点击帮助区域外时关闭弹层 */
function onDocumentClick(e) {
  if (!isOpen.value) return;
  const el = e.target;
  if (el instanceof Element && el.closest('.help-wrap')) return;
  isOpen.value = false;
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick);
});
</script>

<style scoped lang="scss">
.help-wrap {
  position: relative;
  display: inline-flex;
}

.btn--help {
  width: 32px;
  min-width: 32px;
  height: 32px;
  padding: 0;
  justify-content: center;
  font-weight: 700;
  border-radius: 50%;

  &.is-open {
    background: var(--pd-primary-soft);
    border-color: var(--pd-primary);
    color: var(--pd-primary);
  }
}

.help-popover {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 100;
  min-width: 260px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 10px;
  box-shadow: var(--pd-shadow-card);
}

.help-popover__title {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 10px;
}

.help-popover__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.help-popover__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--pd-text);

  kbd {
    display: inline-block;
    min-width: 22px;
    padding: 2px 6px;
    font-size: 10px;
    font-family: inherit;
    text-align: center;
    background: var(--pd-bg-sunken);
    border: 1px solid var(--pd-border-subtle);
    border-radius: 4px;
  }

  span:last-child {
    flex: 1;
    color: var(--pd-text-muted);
  }
}

.help-popover__label {
  font-size: 11px;
  color: var(--pd-text-muted);
  min-width: 48px;
}
</style>
