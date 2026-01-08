<template>
  <div id="userRegisterPage">
    <div class="register-container">
      <div class="register-header">
        <div class="logo-section">
          <span class="logo-text">AI Code</span>
        </div>
        <h1 class="register-title">用户注册</h1>
        <p class="register-subtitle">创建您的账号开始使用</p>
      </div>

      <a-form
        :model="formState"
        name="register"
        autocomplete="off"
        @finish="handleSubmit"
        class="register-form"
      >
        <a-form-item
          name="userAccount"
          :rules="[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '请输入正确的邮箱格式' }
          ]"
          class="form-item"
        >
          <a-input
            v-model:value="formState.userAccount"
            placeholder="请输入邮箱"
            size="large"
            class="form-input"
          >
            <template #prefix>
              <span class="input-icon">📧</span>
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少8位' }
          ]"
          class="form-item"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="请输入密码（至少8位）"
            size="large"
            class="form-input"
          >
            <template #prefix>
              <span class="input-icon">🔒</span>
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item
          name="checkPassword"
          :rules="[
            { required: true, message: '请确认密码' },
            { min: 8, message: '密码至少8位' },
            { validator: validateConfirmPassword }
          ]"
          class="form-item"
        >
          <a-input-password
            v-model:value="formState.checkPassword"
            placeholder="请再次输入密码"
            size="large"
            class="form-input"
          >
            <template #prefix>
              <span class="input-icon">🔒</span>
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item
          name="verifyCode"
          :rules="[
            { required: true, message: '请输入验证码' },
            { len: 6, message: '验证码为6位数字' }
          ]"
          class="form-item"
        >
          <div class="verify-code-container">
            <a-input
              v-model:value="formState.verifyCode"
              placeholder="请输入6位验证码"
              size="large"
              class="verify-code-input"
              maxlength="6"
            >
              <template #prefix>
                <span class="input-icon">🔐</span>
              </template>
            </a-input>
            <a-button
              type="primary"
              size="large"
              class="send-code-btn"
              :loading="sendingCode"
              :disabled="!canSendCode || countdown > 0"
              @click="handleSendVerifyCode"
            >
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </a-button>
          </div>
        </a-form-item>

        <a-form-item class="form-item">
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            class="register-btn"
            :loading="loading"
          >
            注册
          </a-button>
        </a-form-item>
      </a-form>

      <div class="register-footer">
        <p class="register-tip">
          已有账号？
          <router-link to="/user/login" class="login-link">立即登录</router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { userRegister, sendVerifyCode } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'

const formState = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  verifyCode: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let countdownTimer: NodeJS.Timeout | null = null

// 计算是否可以发送验证码
const canSendCode = computed(() => {
  return formState.userAccount && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formState.userAccount)
})

// 校验确认密码
const validateConfirmPassword = async (_rule: any, value: string) => {
  if (value !== formState.userPassword) {
    return Promise.reject(new Error('两次输入的密码不一致'))
  }
  return Promise.resolve()
}

/**
 * 发送验证码
 */
const handleSendVerifyCode = async () => {
  if (!canSendCode.value || countdown.value > 0) return

  sendingCode.value = true
  try {
    const res = await sendVerifyCode({
      email: formState.userAccount,
    })

    if (res.data.code === 0) {
      message.success('验证码已发送到您的邮箱')
      startCountdown()
    } else {
      message.error('发送验证码失败：' + res.data.message)
    }
  } catch (error) {
    message.error('发送验证码失败，请重试')
  } finally {
    sendingCode.value = false
  }
}

/**
 * 开始倒计时
 */
const startCountdown = () => {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }, 1000)
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  loading.value = true
  try {
    const res = await userRegister({
      userAccount: values.userAccount,
      userPassword: values.userPassword,
      checkPassword: values.checkPassword,
      verifyCode: values.verifyCode
    } as API.UserRegisterRequest)

    if (res.data.code === 0) {
      message.success('注册成功，请登录')
      router.push({
        path: '/user/login',
        replace: true,
      })
    } else {
      message.error('注册失败，' + res.data.message)
    }
  } catch (error) {
    message.error('注册失败，请重试')
  } finally {
    loading.value = false
  }
}

// 组件卸载时清理定时器
onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
#userRegisterPage {
  min-height: 100vh;
  background: linear-gradient(135deg, #f0f8ff 0%, #e6f3ff 50%, #f5f9ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
}

/* 背景装饰 */
#userRegisterPage::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(circle at 20% 80%, rgba(24, 144, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(64, 169, 255, 0.08) 0%, transparent 50%);
  pointer-events: none;
}

.register-container {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 48px 40px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(24, 144, 255, 0.1);
  position: relative;
  z-index: 2;
}

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-section {
  margin-bottom: 24px;
}

.logo-text {
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.5px;
}

.register-title {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px;
  color: #1a1a1a;
}

.register-subtitle {
  font-size: 16px;
  color: #666;
  margin: 0;
  font-weight: 400;
}

.register-form {
  margin-bottom: 24px;
}

.form-item {
  margin-bottom: 20px;
}

.form-input {
  border-radius: 8px;
  border: 1px solid #d9e7f5;
  background: rgba(255, 255, 255, 0.8);
  transition: all 0.3s ease;
}

.form-input:focus,
.form-input:hover {
  border-color: #1890ff;
  background: rgba(255, 255, 255, 1);
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.input-icon {
  font-size: 16px;
  color: #999;
}

.verify-code-container {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.verify-code-input {
  flex: 1;
  border-radius: 8px;
  border: 1px solid #d9e7f5;
  background: rgba(255, 255, 255, 0.8);
  transition: all 0.3s ease;
}

.verify-code-input:focus,
.verify-code-input:hover {
  border-color: #1890ff;
  background: rgba(255, 255, 255, 1);
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.send-code-btn {
  height: 40px;
  border-radius: 8px;
  font-weight: 500;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(82, 196, 26, 0.3);
  transition: all 0.3s ease;
  white-space: nowrap;
  min-width: 100px;
}

.send-code-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #73d13d 0%, #95de64 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.4);
}

.send-code-btn:disabled {
  background: #f5f5f5;
  color: #bfbfbf;
  cursor: not-allowed;
}

.register-btn {
  width: 100%;
  height: 48px;
  border-radius: 8px;
  font-weight: 500;
  font-size: 16px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  border: none;
  box-shadow: 0 4px 16px rgba(24, 144, 255, 0.3);
  transition: all 0.3s ease;
}

.register-btn:hover {
  background: linear-gradient(135deg, #40a9ff 0%, #69c0ff 100%);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(24, 144, 255, 0.4);
}

.register-footer {
  text-align: center;
}

.register-tip {
  color: #666;
  font-size: 14px;
  margin: 0;
}

.login-link {
  color: #1890ff;
  text-decoration: none;
  font-weight: 500;
}

.login-link:hover {
  color: #40a9ff;
  text-decoration: underline;
}

/* 响应式设计 */
@media (max-width: 480px) {
  .register-container {
    padding: 32px 24px;
  }

  .logo-text {
    font-size: 28px;
  }

  .register-title {
    font-size: 24px;
  }

  .register-subtitle {
    font-size: 14px;
  }

  .verify-code-container {
    flex-direction: column;
    gap: 8px;
  }

  .send-code-btn {
    width: 100%;
    height: 40px;
  }
}
</style>