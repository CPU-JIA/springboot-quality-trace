<template>
  <main class="login-page">
    <section class="login-board">
      <div class="login-copy">
        <div class="system-kicker">星辉电器 · 产品质量追踪系统</div>
        <h1>质量追踪工作台</h1>
        <p>面向制造现场的批次台账、三级检验、缺陷处置、出货追溯与召回闭环管理。</p>
        <div class="role-grid">
          <button v-for="item in accountProfiles" :key="item.username" class="role-item" type="button" @click="fillAccount(item)">
            <span>{{ item.role }}</span>
            <strong>{{ item.username }}</strong>
          </button>
        </div>
      </div>

      <div class="login-panel">
        <h2>系统登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="账号" prop="username">
            <el-input v-model.trim="form.username" placeholder="请输入账号" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-button class="login-button" type="primary" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { validateForm } from '../utils/formGuards'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const accountProfiles = [
  { role: '系统管理员', username: 'admin' },
  { role: '仓库人员', username: 'zhangsan' },
  { role: '生产人员', username: 'lisi' },
  { role: '质检员', username: 'wangwu' },
  { role: '质量主管', username: 'zhaoliu' }
]

function fillAccount(item) {
  form.username = item.username
  form.password = '123456'
}

async function submit() {
  if (!(await validateForm(formRef))) return
  loading.value = true
  try {
    await auth.login(form)
    router.push(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 28px;
  background:
    linear-gradient(90deg, rgba(31, 111, 91, 0.08) 1px, transparent 1px),
    linear-gradient(180deg, rgba(31, 111, 91, 0.08) 1px, transparent 1px),
    #eef2ef;
  background-size: 28px 28px;
}

.login-board {
  width: min(1080px, 100%);
  min-height: 620px;
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) 420px;
  border: 1px solid #c7d1cc;
  background: #ffffff;
  border-radius: 8px;
  overflow: hidden;
}

.login-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(34px, 6vw, 76px);
  color: #1d2b26;
  background:
    linear-gradient(135deg, rgba(31, 111, 91, 0.12), transparent 46%),
    linear-gradient(180deg, #f8faf8, #edf3f0);
}

.system-kicker {
  color: #1f6f5b;
  font-size: 13px;
  font-weight: 700;
}

h1 {
  max-width: 620px;
  margin: 18px 0 14px;
  font-size: clamp(34px, 5vw, 54px);
  line-height: 1.08;
  letter-spacing: 0;
}

p {
  max-width: 560px;
  margin: 0;
  color: #5d6d66;
  line-height: 1.8;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-top: 44px;
}

.role-item {
  min-height: 72px;
  padding: 12px;
  color: #1d2b26;
  text-align: left;
  border: 1px solid #c7d1cc;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition:
    border-color 0.14s ease,
    background-color 0.14s ease,
    box-shadow 0.14s ease;
}

.role-item:hover,
.role-item:focus-visible {
  border-color: #1f6f5b;
  background: #ffffff;
  box-shadow: 0 4px 14px rgba(31, 111, 91, 0.12);
  outline: none;
}

.role-item span {
  display: block;
  color: #718078;
  font-size: 12px;
}

.role-item strong {
  display: block;
  margin-top: 9px;
  font-size: 14px;
}

.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px;
  border-left: 1px solid #d9dfdc;
}

.login-panel h2 {
  margin: 0 0 26px;
  font-size: 24px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}

@media (max-width: 900px) {
  .login-board {
    grid-template-columns: 1fr;
  }

  .login-panel {
    border-left: 0;
    border-top: 1px solid #d9dfdc;
  }

  .role-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
