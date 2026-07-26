<template>
  <Teleport to="body">
    <ul
        v-show="visible"
        :style="{ left: `${x}px`, top: `${y}px` }"
        class="flow-node-context-menu"
        role="menu"
        @mousedown.stop
    >
      <li role="menuitem" @click="emit('add-to-chat')">添加到对话</li>
      <li class="is-danger" role="menuitem" @click="emit('delete')">删除节点</li>
    </ul>
  </Teleport>
</template>

<script setup lang="ts">
/** 画布节点右键菜单 */
defineProps<{
  visible: boolean;
  x: number;
  y: number;
}>();

const emit = defineEmits<{
  'add-to-chat': [];
  delete: [];
}>();
</script>

<style scoped lang="scss">
.flow-node-context-menu {
  position: fixed;
  z-index: 200;
  margin: 0;
  padding: 4px 0;
  min-width: 148px;
  list-style: none;
  background: #fff;
  border: 1px solid #d6e6f5;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.14);
  font-size: 13px;
  color: #0f172a;

  li {
    padding: 8px 14px;
    cursor: pointer;
    line-height: 1.4;
    white-space: nowrap;

    &:hover {
      background: rgba(11, 110, 220, 0.08);
      color: #0b6edc;
    }

    &.is-danger:hover {
      background: rgba(220, 38, 38, 0.08);
      color: #dc2626;
    }
  }
}
</style>
