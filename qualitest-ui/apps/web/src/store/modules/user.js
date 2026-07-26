import router from '@/router'
import { ElMessageBox, } from 'element-plus'
import { login, logout, getInfo as fetchUserInfo } from '@/api/login'
import { getConfigKey } from '@/api/system/config'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { isHttp, isEmpty } from "@/utils/validate"
import defAva from '@/assets/images/profile.jpg'
import useSettingsStore from '@/store/modules/settings'

const useUserStore = defineStore(
  'user',
  {
    state: () => ({
      token: getToken(),
      id: '',
      name: '',
      nickName: '',
      avatar: '',
      roles: [],
      permissions: []
    }),
    actions: {
      /** 从系统参数同步侧边栏主题（未在本地 layout-setting 中保存过时生效） */
      applySideThemeFromConfig() {
        try {
          const raw = localStorage.getItem('layout-setting')
          const layout = raw ? JSON.parse(raw) : null
          if (layout && Object.prototype.hasOwnProperty.call(layout, 'sideTheme')) {
            return Promise.resolve()
          }
        } catch {
          return Promise.resolve()
        }
        return getConfigKey('sys.index.sideTheme')
          .then((res) => {
            const v = String(res.msg || '').trim()
            if (v !== 'theme-dark' && v !== 'theme-light') return
            const settingsStore = useSettingsStore()
            settingsStore.changeSetting({ key: 'sideTheme', value: v })
            let layout = {}
            try {
              layout = JSON.parse(localStorage.getItem('layout-setting') || '{}') || {}
            } catch {
              layout = {}
            }
            layout.sideTheme = v
            localStorage.setItem('layout-setting', JSON.stringify(layout))
          })
          .catch(() => {})
      },
      // 登录
      login(userInfo) {
        const username = userInfo.username.trim()
        const password = userInfo.password
        const code = userInfo.code
        const uuid = userInfo.uuid
        return new Promise((resolve, reject) => {
          login(username, password, code, uuid).then(res => {
            setToken(res.token)
            this.token = res.token
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 获取用户信息
      getInfo() {
        return new Promise((resolve, reject) => {
          fetchUserInfo().then(res => {
            const user = res.user
            let avatar = user.avatar || ""
            if (!isHttp(avatar)) {
              avatar = (isEmpty(avatar)) ? defAva : import.meta.env.VITE_APP_BASE_API + avatar
            }
            if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.id = user.userId
            this.name = user.userName
            this.nickName = user.nickName
            this.avatar = avatar
            /* 初始密码提示 */
            if(res.isDefaultModifyPwd) {
              ElMessageBox.confirm('您的密码还是初始密码，请修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            /* 过期密码提示 */
            if(!res.isDefaultModifyPwd && res.isPasswordExpired) {
              ElMessageBox.confirm('您的密码已过期，请尽快修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            this.applySideThemeFromConfig().finally(() => resolve(res))
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 退出系统
      logOut() {
        return new Promise((resolve, reject) => {
          logout(this.token).then(() => {
            this.token = ''
            this.roles = []
            this.permissions = []
            removeToken()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      }
    }
  })

export default useUserStore
