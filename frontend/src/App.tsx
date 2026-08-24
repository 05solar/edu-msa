import { useApp } from './state/AppContext'
import { Sidebar } from './components/Sidebar/Sidebar'
import { Topbar } from './components/Topbar/Topbar'
import { Footer } from './components/Footer/Footer'
import { Toasts } from './components/Toasts/Toasts'
import { ModalRoot } from './components/ModalRoot/ModalRoot'
import { Login } from './pages/Login/Login'
import { Home } from './pages/Home/Home'
import { List } from './pages/List/List'
import { Detail } from './pages/Detail/Detail'
import { Register } from './pages/Register/Register'
import { My } from './pages/My/My'
import { Ai } from './pages/Ai/Ai'
import { Admin } from './pages/Admin/Admin'

export function App() {
  const { loggedIn, view, sideCollapsed } = useApp()

  if (!loggedIn) {
    return (
      <>
        <Login />
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
