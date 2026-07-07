<template>
  <section class="page">
    <PageTitle title="用户管理" subtitle="维护系统账号、角色授权、账号启停与密码重置。">
      <template #actions>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </template>
    </PageTitle>

    <div class="work-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="query.keyword" clearable placeholder="账号 / 姓名" style="width: 260px" @keyup.enter="load" />
          <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="page.records" stripe style="margin-top: 14px">
        <el-table-column prop="username" label="账号" width="140" />
        <el-table-column prop="realName" label="姓名" width="130" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <div class="status-line">
              <el-tag v-for="role in row.roles" :key="role.id" size="small" effect="plain">
                {{ role.roleName }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="390" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button text :icon="Key" @click="openPassword(row)">重置密码</el-button>
              <el-button text :type="row.status === 1 ? 'warning' : 'success'" :disabled="isCurrentUser(row)" @click="toggleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button text type="danger" :icon="Delete" :disabled="isCurrentUser(row)" @click="removeUser(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :page-sizes="pageSizeOptions"
          layout="total, sizes, prev, pager, next"
          :total="page.total"
          @change="load"
        />
      </div>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.editing ? '编辑用户' : '新增用户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="账号" prop="username">
          <el-input v-model.trim="form.username" :disabled="dialog.editing" />
        </el-form-item>
        <el-form-item v-if="!dialog.editing" label="初始密码" prop="password">
          <el-input v-model="form.password" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model.trim="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model.trim="form.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-checkbox-group v-model="form.roleIds">
            <el-checkbox v-for="role in roles" :key="role.id" :value="role.id" :disabled="isSelfAdminRole(role)">
              {{ role.roleName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialog.visible" title="重置密码" width="420px">
      <el-form ref="passwordRef" :model="passwordForm" :rules="passwordRules" label-width="92px">
        <el-form-item label="用户">
          <span>{{ passwordDialog.row?.realName }}（{{ passwordDialog.row?.username }}）</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="resetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ElMessageBox } from 'element-plus/es/components/message-box/index'
import { Delete, Edit, Key, Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageTitle from '../components/PageTitle.vue'
import { userApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { pageSizeOptions } from '../utils/dicts'
import { validateForm } from '../utils/formGuards'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const roles = ref([])
const formRef = ref()
const passwordRef = ref()
const query = reactive({ current: 1, size: 10, keyword: '' })
const page = reactive({ records: [], total: 0 })
const dialog = reactive({ visible: false, editing: false, row: null })
const passwordDialog = reactive({ visible: false, row: null })
const currentUserId = computed(() => String(auth.user?.userId || ''))

const form = reactive({ username: '', password: '', realName: '', phone: '', roleIds: [] })
const passwordForm = reactive({ newPassword: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度须为6~32位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleIds: [{ type: 'array', required: true, message: '请至少选择一个角色', trigger: 'change' }]
}

const passwordRules = {
  newPassword: [{ required: true, min: 6, max: 32, message: '密码长度须为6~32位', trigger: 'blur' }]
}

onMounted(async () => {
  roles.value = await userApi.roles()
  await load()
})

async function load() {
  loading.value = true
  try {
    Object.assign(page, await userApi.page(query))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.visible = true
  dialog.editing = false
  dialog.row = null
  Object.assign(form, { username: '', password: '', realName: '', phone: '', roleIds: [] })
}

function openEdit(row) {
  dialog.visible = true
  dialog.editing = true
  dialog.row = row
  Object.assign(form, {
    username: row.username,
    password: '',
    realName: row.realName,
    phone: row.phone,
    roleIds: (row.roles || []).map((role) => role.id)
  })
}

async function save() {
  if (!(await validateForm(formRef))) return
  const adminRole = roles.value.find((role) => role.roleCode === 'ADMIN')
  if (dialog.editing && isCurrentUser(dialog.row) && adminRole && !form.roleIds.includes(adminRole.id)) {
    ElMessage.warning('不能移除自己的管理员角色')
    form.roleIds.push(adminRole.id)
    return
  }
  saving.value = true
  try {
    if (dialog.editing) {
      await userApi.update(dialog.row.id, form)
    } else {
      await userApi.create(form)
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

function openPassword(row) {
  passwordDialog.visible = true
  passwordDialog.row = row
  passwordForm.newPassword = ''
}

async function resetPassword() {
  if (!(await validateForm(passwordRef))) return
  await userApi.resetPassword(passwordDialog.row.id, passwordForm)
  ElMessage.success('密码已重置')
  passwordDialog.visible = false
}

async function toggleStatus(row) {
  if (isCurrentUser(row)) {
    ElMessage.warning('不能停用当前登录账号')
    return
  }
  const next = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '停用'}账号 ${row.username}？`, '账号状态')
  await userApi.changeStatus(row.id, next)
  ElMessage.success('状态已更新')
  await load()
}

async function removeUser(row) {
  if (isCurrentUser(row)) {
    ElMessage.warning('不能删除当前登录账号')
    return
  }
  await ElMessageBox.confirm(`确认删除用户 ${row.username}？已有业务引用的用户会被后端拒绝删除。`, '删除确认')
  await userApi.delete(row.id)
  ElMessage.success('用户已删除')
  await load()
}

function isCurrentUser(row) {
  return Boolean(row?.id) && String(row.id) === currentUserId.value
}

function isSelfAdminRole(role) {
  return dialog.editing && isCurrentUser(dialog.row) && role.roleCode === 'ADMIN'
}
</script>
