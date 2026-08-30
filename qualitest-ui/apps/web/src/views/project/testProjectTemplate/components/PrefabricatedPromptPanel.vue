<template>
  <div class="tpl-prefab-section prefab-prompt-panel">
    <div class="tpl-prefab-section__head">
      <span class="tpl-prefab-section__title">预制提示词</span>
      <div v-if="!readOnly" class="tpl-prefab-section__actions">
        <el-button icon="Plus" size="small" type="primary" @click="handleAdd">新增</el-button>
        <el-button :disabled="selectedIndex < 0" icon="Delete" size="small" @click="handleRemove">删除</el-button>
      </div>
    </div>
    <div class="tpl-prefab-section__body">
      <p v-if="!readOnly" class="tpl-prefab-section__hint">
        可选。勾选模板时若有条目则种子为项目级 AI 快捷词（同场景+标题已存在则跳过）。鉴权模板可留空；靶场业务步骤与测值放在提示集/项目自建芯片，勿写进通用登录模板。
      </p>
      <el-table
        v-if="rows.length"
        :data="rows"
        border
        class="tpl-prefab-section__table"
        highlight-current-row
        row-key="_index"
        size="small"
        @current-change="onCurrentChange"
      >
        <el-table-column label="标题" min-width="140" prop="title" show-overflow-tooltip>
          <template #default="scope">
            <el-input
              v-if="!readOnly"
              v-model="list[scope.row._index].title"
              maxlength="100"
              placeholder="标题"
              size="small"
            />
            <span v-else>{{ scope.row.title || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="120" prop="description" show-overflow-tooltip>
          <template #default="scope">
            <el-input
              v-if="!readOnly"
              v-model="list[scope.row._index].description"
              maxlength="200"
              placeholder="胶囊悬停，可选"
              size="small"
            />
            <span v-else>{{ scope.row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="正文" min-width="280" prop="content" show-overflow-tooltip>
          <template #default="scope">
            <el-input
              v-if="!readOnly"
              v-model="list[scope.row._index].content"
              :rows="2"
              placeholder="短意图正文"
              size="small"
              type="textarea"
            />
            <span v-else>{{ scope.row.content || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column align="center" label="排序" width="100" prop="sortNum">
          <template #default="scope">
            <el-input-number
              v-if="!readOnly"
              v-model="list[scope.row._index].sortNum"
              :controls="false"
              :min="0"
              size="small"
            />
            <span v-else>{{ scope.row.sortNum ?? 0 }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else
        :class="readOnly ? 'tpl-prefab-section__empty--compact' : 'tpl-prefab-section__empty'"
        :image-size="48"
        description="可选：业务场景短提示，勾选模板时种子到项目"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 预制 AI 提示词面板：编辑 templatePrompts。
 * Apply 时写入项目级 ai_prompt_template；内置模板只读。
 */
import { computed, ref, watch } from 'vue'
import { emptyPrefabPrompt, parsePrompts } from '../utils/templateForm'

const props = defineProps({
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})

const list = defineModel({ type: Array, default: () => [] })

const selectedIndex = ref(-1)

const prompts = computed(() => parsePrompts(list.value))

const rows = computed(() =>
  prompts.value.map((row, index) => ({
    _index: index,
    title: String(row?.title || '').trim(),
    description: String(row?.description || '').trim(),
    content: String(row?.content || '').trim(),
    sortNum: row?.sortNum ?? 0,
  })),
)

watch(
  () => list.value,
  () => {
    if (selectedIndex.value >= prompts.value.length) selectedIndex.value = -1
  },
)

function handleAdd() {
  if (props.readOnly) return
  list.value = [...prompts.value, emptyPrefabPrompt()]
  selectedIndex.value = list.value.length - 1
}

function handleRemove() {
  if (props.readOnly || selectedIndex.value < 0) return
  list.value = prompts.value.filter((_, i) => i !== selectedIndex.value)
  selectedIndex.value = -1
}

function onCurrentChange(row) {
  selectedIndex.value = row?._index ?? -1
}
</script>

<style lang="scss">
@use '../styles/templatePrefabPanel.scss';
</style>
