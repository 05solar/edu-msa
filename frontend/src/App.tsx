import { useApp } from './state/AppContext'
import { Sidebar } from './components/Sidebar/Sidebar'
import { Topbar } from './components/Topbar/Topbar'
import { Footer } from './components/Footer/Footer'
import { Toasts } from './components/Toasts/Toasts'
import { ModalRoot } from './components/ModalRoot/ModalRoot'
import { Login } from './pages/Auth/Login'
import { Signup } from './pages/Auth/Signup'
import { FindId } from './pages/Auth/FindId'
import { FindPassword } from './pages/Auth/FindPassword'
import { Home } from './pages/Home/Home'
import { List } from './pages/List/List'
import { Detail } from './pages/Detail/Detail'
import { Register } from './pages/Register/Register'
import { My } from './pages/My/My'
import { Ai } from './pages/Ai/Ai'
import { Admin } from './pages/Admin/Admin'

export function App() {
  const { loggedIn, authReady, authView, view, sideCollapsed } = useApp()

  // 새로고침 직후 세션 복구가 끝나기 전에는 로그인 화면이 잠깐 보이지 않도록 비워 둔다.
  if (!loggedIn && !authReady) return null

  if (!loggedIn) {
    const authPage = {
      login: <Login />,
      signup: <Signup />,
      'find-id': <FindId />,
      'find-pw': <FindPassword />,
    }[authView]

    return (
      <>
        {authPage}
        <Toasts />
      </>
    )
  }

  const page = {
    home: <Home />,
    list: <List />,
    detail: <Detail />,
    register: <Register />,
    my: <My />,
    ai: <Ai />,
    admin: <Admin />,
  }[view]

  return (
    <>
      <div className={`app-shell${sideCollapsed ? ' rail' : ''}`}>
        <Sidebar />
        <div className="main-area">
          <Topbar />
          <main>{page}</main>
          <Footer />
        </div>
      </div>
      <Toasts />
      <ModalRoot />
    </>
  )
}
