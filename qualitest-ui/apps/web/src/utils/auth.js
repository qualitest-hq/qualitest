import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'

/** file:// 下 document.cookie 不可用，桌面端改用 localStorage */
function useLocalTokenStorage() {
  return (
    typeof window !== 'undefined' &&
    window.__QUALITEST_ELECTRON__?.transport === 'electron-main'
  )
}

export function getToken() {
  if (useLocalTokenStorage()) {
    return localStorage.getItem(TokenKey) || ''
  }
  return Cookies.get(TokenKey)
}

export function setToken(token) {
  if (useLocalTokenStorage()) {
    if (token) {
      localStorage.setItem(TokenKey, token)
    } else {
      localStorage.removeItem(TokenKey)
    }
    return token
  }
  return Cookies.set(TokenKey, token)
}

export function removeToken() {
  if (useLocalTokenStorage()) {
    localStorage.removeItem(TokenKey)
    return
  }
  return Cookies.remove(TokenKey)
}
