import { useApp } from '../../state/AppContext'

export function ModalRoot() {
  const { modal, closeModal } = useApp()
  if (!modal) return null
  return (
    <div className="modal-back" onClick={(e) => { if (e.target === e.currentTarget) closeModal() }}>
      {modal}
    </div>
  )
}
